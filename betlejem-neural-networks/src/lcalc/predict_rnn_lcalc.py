import datetime
import json
import multiprocessing

import pandas as pd
from kafka import KafkaConsumer
from kafka import KafkaProducer
from numpy import reshape

import tensorflow as tf

# IMPORTANT: this one must be matching training settings from: me/ugeno/betlejem/lcalc/LcalcTrainerApplication.java
RNN_SEQ_LEN = 96  # number of feature in sequence

BOOTSTRAP_SERVERS = 'localhost:9093'

MARKET = 'crypto'
EVAL_OUTPUT_PATH = '..\\..\\data\\%s\\evaluation' % MARKET
MODELS_INPUT_PATH = '..\\..\\model\\selected\\%s\\' % MARKET

MIN_BUY_TOTAL_PRICE = 3000  # PLN
PRINT_TOP = 30

SEQ_ELEM_SIZE = 4
TOTAL_SEQ_SIZE = RNN_SEQ_LEN * SEQ_ELEM_SIZE

MODEL_FILES = dict({
    "rnn_ETHUSDT_1m_x96_v11_ETHUSDT_1m.past096_forward002_000_30-0.569-20220620-0845.model": ["ETHUSDT_1m", 0.569],
})

consumer = KafkaConsumer(
    bootstrap_servers=[BOOTSTRAP_SERVERS],
    auto_offset_reset='latest',
    enable_auto_commit=False,
    group_id='eval-kafka-trigger',
    value_deserializer=lambda x: json.loads(x.decode('utf-8')))

producer = KafkaProducer(
    bootstrap_servers=[BOOTSTRAP_SERVERS],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)


def handle_predictions():
    models = dict({})
    model_aliases = MODEL_FILES.keys()
    for i, model_filename in enumerate(model_aliases):
        print("Loading model %s" % model_filename)
        model_pair_name = MODEL_FILES[model_filename][0]
        models[model_pair_name] = tf.keras.models.load_model(MODELS_INPUT_PATH + model_filename)

    print("All models loaded...")

    topic = 'ready_for_eval'
    consumer.subscribe(topics=[topic])
    print("Subscribed for %s" % topic)

    for message in consumer:
        value = message.value
        print("\n\n============ Incoming message ============")

        input_filename = value
        output_filename = "results_%s" % value

        dataset = pd.read_csv('%s\\%s' % (EVAL_OUTPUT_PATH, input_filename), header=None)
        total = len(dataset)

        dataset.rename(columns={0: 'date'}, inplace=True)
        dataset.rename(columns={1: 'name'}, inplace=True)
        dataset.rename(columns={2: 'price'}, inplace=True)
        rnn_feature_set = dataset.drop(columns=['date', 'name', 'price']).values

        output_data = pd.DataFrame()
        output_const = dataset[['date', 'name', 'price']]
        input_data = reshape(rnn_feature_set, (len(output_const), RNN_SEQ_LEN, SEQ_ELEM_SIZE))

        for model_pair_name in models.keys():
            if model_pair_name in input_filename:
                print("Model pair name %s matches input filename %s - applying... Total dataset size: %s" % (model_pair_name, input_filename, total))

                prediction = models[model_pair_name].predict(input_data)

                new_record = pd.concat([output_const, pd.DataFrame(prediction[:, :], columns=["sell", "pass", "buy"])], axis=1)
                output_data = output_data.append(new_record)

                output_data["sell"] = round((output_data["sell"]), 2)
                output_data["pass"] = round((output_data["pass"]), 2)
                output_data["buy"] = round((output_data["buy"]), 2)

                pd.set_option('display.max_columns', 20)
                pd.set_option('display.width', 1000)

                timestamp = datetime.datetime.now().strftime("%Y%m%d%H%M")
                print("============ Recommendations for ============ (" + timestamp + ")")
                output_data = output_data[['date', 'name', 'price', 'sell', 'pass', 'buy']].head(PRINT_TOP)
                output_data.to_csv('%s\\%s' % (EVAL_OUTPUT_PATH, output_filename), index=False, header=True)
                print(output_data.to_string(index=False, header=True))

                producer.send(
                    ('%s_eval_finished' % model_pair_name),  # TODO: Push directly to specific eval topic
                    value={
                        # {
                        #     "date": output_data['date'].tail(1),
                        #     "name": output_data['name'].tail(1),
                        #     "price": output_data['price'].tail(1),
                        #     "sell": output_data['sell'].tail(1),
                        #     "pass": output_data['pass'].tail(1),
                        #     "buy": output_data['buy'].tail(1)
                        # }
                        "time": timestamp
                    }
                )
                producer.flush()
    pass


if __name__ == '__main__':
    print(tf.__version__)

    while True:
        training_process = multiprocessing.Process(target=handle_predictions, args=())
        training_process.start()
        training_process.join()
