package za.co.vodacom.vodacommft;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import za.co.vodacom.vodacommft.service.IThreadTuningService;
import za.co.vodacom.vodacommft.service.impl.ThreadTuningService;

/*@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages ="za.co.vodacom")
@EnableAspectJAutoProxy(proxyTargetClass = true)*/
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class VodacommftApplication {

    public static void main(String[] args) {
        SpringApplication.run(VodacommftApplication.class, args);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public IThreadTuningService prototypeServiceBean(){
        return new ThreadTuningService();
    }
}
