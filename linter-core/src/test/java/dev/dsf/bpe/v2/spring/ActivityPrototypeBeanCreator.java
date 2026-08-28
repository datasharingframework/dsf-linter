package dev.dsf.bpe.v2.spring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test double for DSF API V2 {@code ActivityPrototypeBeanCreator}.
 * Same type name, constructor and {@code activities} field as the production class
 * so {@code SpringConfigurationLinter} can extract registered activity types.
 */
public class ActivityPrototypeBeanCreator {

    @SuppressWarnings("unused") // read reflectively by SpringConfigurationLinter
    private final List<Class<?>> activities = new ArrayList<>();

    public ActivityPrototypeBeanCreator(Class<?>... activities) {
        if (activities != null) {
            Collections.addAll(this.activities, activities);
        }
    }
}
