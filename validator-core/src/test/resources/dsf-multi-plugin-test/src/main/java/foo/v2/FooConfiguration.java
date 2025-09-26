package foo.v2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.dsf.bpe.v2.spring.ActivityPrototypeBeanCreator;

@Configuration
public class FooConfiguration
{
	@Bean
	public static ActivityPrototypeBeanCreator activityPrototypeBeanCreator()
	{
		return new ActivityPrototypeBeanCreator(Foo.class);
	}
}
