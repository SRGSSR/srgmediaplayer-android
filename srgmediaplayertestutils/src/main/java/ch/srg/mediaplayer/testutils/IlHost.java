package ch.srg.mediaplayer.testutils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by npietri on 25.10.16.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface IlHost {
    String value();
}
