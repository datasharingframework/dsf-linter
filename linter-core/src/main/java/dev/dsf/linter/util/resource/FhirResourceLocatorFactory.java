package dev.dsf.linter.util.resource;

import java.io.File;

public final class FhirResourceLocatorFactory {
    private FhirResourceLocatorFactory() {

    }

    public static FhirResourceLocator getResourceLocator(File projectRoot) {
        return FhirResourceLocator.create(projectRoot);
    }
}
