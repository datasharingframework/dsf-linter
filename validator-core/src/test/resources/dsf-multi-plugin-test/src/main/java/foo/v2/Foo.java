package foo.v2;

import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.variables.Variables;

public class Foo implements ServiceTask
{
	@Override
	public void execute(ProcessPluginApi processPluginApi, Variables variables) throws ErrorBoundaryEvent, Exception
	{
		System.out.println("Foo v2!");
	}
}
