package ch.srg.mediaplayer.demo.test;

import javax.inject.Singleton;

import ch.srg.dataProvider.integrationlayer.performance.PerformanceProfiler;
import dagger.Module;
import dagger.Provides;

/**
 * Created by npietri on 01.07.15.
 */
@Module(
        library = true,
        overrides = true,
        complete = false
)
public class DisablePerfomanceProfileModule {
        @Provides
        @Singleton
        PerformanceProfiler providePerformanceProfiler() {
                return new DisabledPerformanceProfiler();
        }

}
