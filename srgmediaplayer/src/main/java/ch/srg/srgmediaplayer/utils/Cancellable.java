package ch.srg.srgmediaplayer.utils;

public interface Cancellable {
    Cancellable NOT_CANCELLABLE = new Cancellable() {
        @Override
        public void cancel() {
        }
    };

    void cancel();
}
