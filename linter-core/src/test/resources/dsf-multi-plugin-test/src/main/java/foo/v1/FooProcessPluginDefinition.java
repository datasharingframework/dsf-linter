package foo.v1;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import dev.dsf.bpe.v1.ProcessPluginDefinition;

public class FooProcessPluginDefinition implements ProcessPluginDefinition
{
	@Override
	public String getName()
	{
		return "Foo";
	}

	@Override
	public String getVersion()
	{
		return "1.0.0.0";
	}

	@Override
	public LocalDate getReleaseDate()
	{
		return LocalDate.now();
	}

	@Override
	public List<String> getProcessModels()
	{
		return List.of("bpe/v1/foo.bpmn");
	}

	@Override
	public Map<String, List<String>> getFhirResourcesByProcessId()
	{
		return Map.of("foobar_foo", List.of("fhir/ActivityDefinition/foo.xml", "fhir/StructureDefinition/start-foo.xml", "fhir/Task/task-start-foo.xml"));
	}

	@Override
	public List<Class<?>> getSpringConfigurations()
	{
		return List.of(FooConfiguration.class);
	}
}
