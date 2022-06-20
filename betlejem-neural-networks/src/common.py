import tensorflow as tf

MIN_ACC_TO_SAVE_MODEL = 0.51


class CustomModelCheckpoint(tf.keras.callbacks.ModelCheckpoint):
    acc_diff_sum = 0
    max_val_acc = 0
    max_acc_sum = 0

    def __init__(self, filepath, monitor='val_loss', verbose=0, save_best_only=False, save_weights_only=False, mode='auto', save_freq='epoch', options=None, **kwargs):
        super().__init__(filepath, monitor, verbose, save_best_only, save_weights_only, mode, save_freq, options, **kwargs)

    def on_epoch_end(self, epoch, logs=None):
        print("\n--- EPOCH END ---")

        acc = logs['accuracy']
        val_acc = logs['val_accuracy']
        acc_diff = val_acc - acc

        if acc_diff < 0:  # abs(...)
            acc_diff *= -1

        print("\n------------------------------------------------------------------------------------------------------------------")
        print(f"epoch: {epoch}, training_acc: {round(float(acc), 4)}, validation_acc: {round(float(val_acc), 4)} diff: {round(float(acc_diff), 4)}")
        print("--------------------------------------------------------------------------------------------------------------------\n")

        self.acc_diff_sum += acc_diff
        acc_diff_avg = self.acc_diff_sum / (epoch + 1)
        acc_sum = acc + val_acc

        if MIN_ACC_TO_SAVE_MODEL <= acc:
            if self.max_val_acc <= val_acc:
                print("Saving model (max val_acc)...")
                super().on_epoch_end(epoch, logs)
            elif acc_diff <= acc_diff_avg and self.max_acc_sum <= acc_sum:
                print("Saving model (max acc_sum and diff below avg)...")
                super().on_epoch_end(epoch, logs)

            if self.max_val_acc < val_acc:
                self.max_val_acc = val_acc

        else:
            print("Skipping save...")

        if self.max_acc_sum < acc_sum:
            self.max_acc_sum = acc_sum
