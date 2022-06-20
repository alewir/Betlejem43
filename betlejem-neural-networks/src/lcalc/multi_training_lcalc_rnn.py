import datetime
import getopt
import multiprocessing
import sys

import pandas as pd
from numpy import reshape, concatenate

import tensorflow as tf
from common import CustomModelCheckpoint
from tensorflow.core.protobuf.config_pb2 import ConfigProto, GPUOptions
from tensorflow.python.client.session import Session

# these must be matching training settings from: me/ugeno/betlejem/lcalc/LcalcTrainerApplication.java
RNN_SEQ_LEN = 96  # IMPORTANT: number of features in sequence <=> network input size
DATASET_VER = 11  # just needs to match the one from trainer component
DATASET_LOOK_FORWARD = 2  # just needs to match the one from trainer component

DATASET_NAME = 'ETHUSDT_1m'
DATASET_NONCE = '000'  # NOTE: nonce is incremented (by trainer component) when input with based on same params is generated twice (to avoid accidental override)

DATA_INPUT_PATH = '..\\..\\data\\crypto'
MODEL_OUTPUT_PATH = '..\\..\\model\\training'

EPOCHS = 80
SEQ_ELEM_SIZE = 4
L_AMOUNT = 3  # number of features

BATCH_SIZE = 256
TEST_SET_RATIO = 0.05
TEST_SET_SPLIT_PERCENTAGE = 0  # begin:end split percentage 0~99%

LEARNING_RATE = 0.001
DECAY = 5e-5
LOSS_FUNC = 'categorical_crossentropy'
DROPOUT = 0.2
TRAINING_TIMESTAMP = datetime.datetime.now().strftime("%Y%m%d-%H%M")


def run_training(data_input_file_param=None):
    rnn_input_seq_len = RNN_SEQ_LEN
    total_seq_size = rnn_input_seq_len * SEQ_ELEM_SIZE
    model_prefix_info = 'v%02d_%s.past%03d_forward%03d_%s' % (DATASET_VER, DATASET_NAME, rnn_input_seq_len, DATASET_LOOK_FORWARD, DATASET_NONCE)

    if data_input_file_param is None or not data_input_file_param:
        input_filename = "%s.csv_pp.csv" % model_prefix_info
        print("Loading data file: %s\\%s" % (DATA_INPUT_PATH, input_filename))
        data_input_file = '%s\\training\\%s' % (DATA_INPUT_PATH, input_filename)
    else:
        print("Loading data file (given as param): %s\\%s" % (DATA_INPUT_PATH, data_input_file_param))
        data_input_file = data_input_file_param
        # TODO: the RNN_SEQ_LEN must be parsed from the name or passed the other way

    dataset = pd.read_csv(data_input_file, delimiter=',', header=None)
    dataset = dataset.drop(columns=[0, 1]).values  # drop columns with additional information (timestamp, symbol)

    begin_split = int(len(dataset) * TEST_SET_RATIO * TEST_SET_SPLIT_PERCENTAGE / 100.)
    end_split = int(len(dataset) * TEST_SET_RATIO * (100 - TEST_SET_SPLIT_PERCENTAGE) / 100.)

    print("Begin split: %d" % begin_split)
    print("End split: %d" % end_split)

    train_data = dataset[begin_split:-end_split]

    test_data1 = dataset[:begin_split]
    test_data2 = dataset[-end_split:]
    test_data = concatenate((test_data1, test_data2), axis=0)  # pick up more from beginning but also some from end of dataset

    tr_f = train_data[:, 0:total_seq_size]
    tr_l = train_data[:, total_seq_size:total_seq_size + L_AMOUNT]

    ts_f = test_data[:, 0:total_seq_size]
    ts_l = test_data[:, total_seq_size:total_seq_size + L_AMOUNT]

    tr_features = reshape(tr_f, (len(tr_f), rnn_input_seq_len, SEQ_ELEM_SIZE))
    ts_features = reshape(ts_f, (len(ts_f), rnn_input_seq_len, SEQ_ELEM_SIZE))

    print("Train data preview:")
    print(tr_features)

    print("Labels preview: %d" % len(tr_l))
    print(tr_l)

    model_prefix = "rnn_%s_x%s_%s" % (DATASET_NAME, rnn_input_seq_len, model_prefix_info)
    model_name = "{}--{}--LR{}-DE{}".format(TRAINING_TIMESTAMP, model_prefix, round(LEARNING_RATE, 5), round(DECAY, 5))

    model = prepare_model(model_name, tr_features.shape[1:], rnn_input_seq_len, total_seq_size)

    model_filepath = "%s\\checkpoints\\%s\\%s_{epoch:02d}-{val_accuracy:.3f}-%s.model" % (MODEL_OUTPUT_PATH, model_name, model_prefix, TRAINING_TIMESTAMP)

    checkpoint = CustomModelCheckpoint(model_filepath,
                                       monitor='val_accuracy',
                                       verbose=1)

    log_dir = "%s\\logs\\fit\\%s.%s" % (MODEL_OUTPUT_PATH, model_name, ".model")
    tb = tf.keras.callbacks.TensorBoard(log_dir=log_dir, histogram_freq=1, profile_batch=0)

    model.fit(tr_features, tr_l,
              shuffle=True,
              epochs=EPOCHS,
              batch_size=BATCH_SIZE,
              validation_data=(ts_features, ts_l),
              callbacks=[checkpoint, tb])


def prepare_model(model_name, tr_features_shape, seq_len, total_seq_len):
    print("Preparing model %s" % model_name)
    print("Input shape: %s" % repr(tr_features_shape))

    model = tf.keras.models.Sequential()

    # NETWORK INPUT
    model.add(tf.keras.layers.LSTM(seq_len, input_shape=tr_features_shape, return_sequences=True))
    model.add(tf.keras.layers.Dropout(DROPOUT))
    model.add(tf.keras.layers.BatchNormalization())

    model.add(tf.keras.layers.LSTM(seq_len, return_sequences=True))
    model.add(tf.keras.layers.Dropout(DROPOUT / 2))
    model.add(tf.keras.layers.BatchNormalization())

    model.add(tf.keras.layers.LSTM(seq_len, return_sequences=True))
    model.add(tf.keras.layers.Dropout(DROPOUT))
    model.add(tf.keras.layers.BatchNormalization())

    model.add(tf.keras.layers.LSTM(seq_len))
    model.add(tf.keras.layers.Dropout(DROPOUT))
    model.add(tf.keras.layers.BatchNormalization())

    model.add(tf.keras.layers.Dense(total_seq_len))
    model.add(tf.keras.layers.Dropout(DROPOUT))
    model.add(tf.keras.layers.BatchNormalization())

    model.add(tf.keras.layers.Dense(total_seq_len / 2))
    model.add(tf.keras.layers.Dropout(DROPOUT))
    model.add(tf.keras.layers.BatchNormalization())

    model.add(tf.keras.layers.Dense(total_seq_len / 4))
    model.add(tf.keras.layers.Dropout(DROPOUT))
    model.add(tf.keras.layers.BatchNormalization())

    # NETWORK OUTPUT
    model.add(tf.keras.layers.Dense(L_AMOUNT, activation=tf.nn.softmax))
    model_opt = tf.keras.optimizers.Adam(LEARNING_RATE, decay=DECAY)
    model.compile(optimizer=model_opt,
                  loss=LOSS_FUNC,
                  metrics=['accuracy'])

    print(model.summary())
    return model


if __name__ == '__main__':
    argv = sys.argv[1:]
    print("ARGV: {0}".format(repr(argv)))
    options, remainder = getopt.getopt(sys.argv[1:], 'd:', ['data='])

    data_input_file_override = ""
    for opt, arg in options:
        if opt in ['-d', '-data']:
            data_input_file_override = arg
    print("Given input filename: {0}".format(repr(data_input_file_override)))

    print("TensorFlow version: {0}".format(repr(tf.__version__)))
    gpu_options = GPUOptions(allow_growth=True, per_process_gpu_memory_fraction=0.95)
    sess = Session(config=ConfigProto(gpu_options=gpu_options))

    training_process = multiprocessing.Process(target=run_training, args=data_input_file_override)
    training_process.start()
    training_process.join()
