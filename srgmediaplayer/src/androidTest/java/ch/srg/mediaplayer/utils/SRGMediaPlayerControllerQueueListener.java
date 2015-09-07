package ch.srg.mediaplayer.utils;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * Created by npietri on 12.06.15.
 */
public class SRGMediaPlayerControllerQueueListener implements SRGMediaPlayerController.Listener {

    private ArrayBlockingQueue<SRGMediaPlayerController.Event> eventBlockingDeque = new ArrayBlockingQueue<>(1000);

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        eventBlockingDeque.add(event);
    }

    public SRGMediaPlayerController.Event getEventInBlockingQueue() throws InterruptedException {
        return eventBlockingDeque.poll(1000, TimeUnit.MILLISECONDS);
    }

    public void clear(){
        eventBlockingDeque.clear();
    }

}
