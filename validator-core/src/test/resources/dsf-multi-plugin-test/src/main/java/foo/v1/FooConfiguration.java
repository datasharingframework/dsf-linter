package foo.v1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import dev.dsf.bpe.v1.ProcessPluginApi;

@Configuration
public class FooConfiguration
{
	@Autowired
	ProcessPluginApi processPluginApi;

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public Foo foo()
	{
		return new Foo(processPluginApi);
	}
}
