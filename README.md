# Betlejem-43

BTC/Binance Electronic Trading and Learning Ensemble of Java Empowered Machines - for free (Betlejem-43).

This is an autonomous Binance trading bot based on Recurrent Neural Network prediction and crypto (default: ETH-USDT pair) accumulation strategy.

It is relatively easy to extend it to handle multiple trading pairs simultaneously, e.g. BTCDSDT, SOLUSDC, BTCETH, etc.

NOTE: It also can be adjusted to work with different markets and exchanges - like stocks - BitBay, XTB. Some prototyping was successful in this area but it is not supported out-of-the-box ATM, needs some coding to make it work in those areas. There are some leftovers in the code to start from but... "needs work".

# Config

## Binance private account API access secrets
```
local/binance.properties
api.key=..
api.sec=..
```
In the above property file the values are expected to be Base64 encoded. One can use, e.g. https://www.base64decode.org/ to do the encoding. NOTE: Although the risk in this particular case seems low, still be careful with online tools and own secrets.

## Necessary directory structure that needs to exist 
```
betlejem-neural-networks
├── data
│   └── crypto
│       ├── evaluation
│       │   ├── ...
│       ├── training
│       │   └── ...
│       └── ...
...
```
NOTE-TODO: To be automated.

# Components

## start Kafka

```
cd betlejem-binance-spot
docker-compose up
```

## start services

### BinancePriceScrapperApplication
```
cd betlejem-binance-spot
.. TODO: cmd for starting from CLI
```

## LcalcEvaluationApplication
```
cd betlejem-lcalc-evaluation-binance
.. TODO: cmd for starting from CLI
```

## LcalcTradebotApplication
```
cd betlejem-lcalc-tradebot-binance
.. TODO: cmd for starting from CLI
```

## start predictions process
```
cd betlejem-neural-networks\src\lcalc
python predict_rnn_lcalc.py
```

# Misc

## Training a new model on the freshest data

### Prepare training data

Run app:
```
me.ugeno.betlejem.lcalc.LcalcTrainerApplication
```

### Run training script
```
lcalc/multi_training_lcalc_rnn.py
```

### [OPTIONAL] Monitor progress on TensorBoard
```
tensorboard --logdir <path_to_tf_logs>
```

Server starts on `http://localhost:6006/`.

Batch script:
```
betlejem-neural-networks/run_board.bat
```

### Pick best model

Get checkpoint from training with best balanced results:
Move it from `betlejem-neural-networks/model/training/checkpoints/{training-sesssion-dir}`
to `betlejem-neural-networks/model/selected/crypto/`

### Add model to script

Update model name for specific pair/ticker in:
`lcalc/predict_rnn_lcalc.py`
Inside of `MODEL_FILES` list variable. 

NOTE-TODO: periodical training and picking up best model could be automated in future.

## IntelliJ TensorFlow imports fix
```
mkdir stubs
mklink /D stubs\tensorflow c:\Python36\Lib\site-packages\tensorflow\
```


## pip cleanup
```
pip3 uninstall Keras-Preprocessing tensorflow tensorboard tensorboard-plugin-wit tensorflow-estimator tensorflow-gpu tb-nightly tensorflow-gpu-estimator tf-estimator-nightly tf-nightly-gpu tf-nightly
```

## CUDA vs TensoFlow versions verified on Windows
```
cuda_10.2.89_441.22_win10.exe
cudnn-11.2-windows-x64-v8.1.0.77.zip
tf-nightly-gpu==2.5.0.dev20210125
```

## Install TensorFlow
```
pip3 install --upgrade tf-nightly-gpu==2.5.0.dev20210125
```

## Other notes and ideas
```
US markets data: https://stooq.com/db/h/
```

## OpenApi issue

There is an issue with generated class:

```
org/openapitools/client/ApiClient.java
```

Around line parameters need to be passed in oposite order as generated, i.e.:
```
1081:  reqBody = RequestBody.create(MediaType.parse(contentType), "");
```
NOTE-TODO: After generating classes this needs to be amended manually until there is some permanent fix for that.