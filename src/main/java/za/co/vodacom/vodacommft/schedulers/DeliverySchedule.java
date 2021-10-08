package za.co.vodacom.vodacommft.schedulers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.vodacom.vodacommft.config.PropertiesFileSysConfig;
import za.co.vodacom.vodacommft.service.IDeliveryDetailsService;
import za.co.vodacom.vodacommft.service.IFileDeliveryService;
import za.co.vodacom.vodacommft.service.impl.PendingDeliveriesService;

import java.util.Date;
import java.util.List;

@EnableAsync
@Component
@Slf4j
public class DeliverySchedule {
    @Autowired
    private IFileDeliveryService deliveryService;

    @Autowired
    IDeliveryDetailsService deliveryDetailsService;

    @Autowired
    PendingDeliveriesService pendingDeliveriesService;

    @Autowired
    private PropertiesFileSysConfig properties_file_sys_config;

    @Scheduled(fixedRateString = "${fixedRate.in.milliseconds}", initialDelayString = "${initialDelay.in.milliseconds}") /*Its Alwyz good to get Values from the Prop Files or DB. Unfortunatels it only accepts application prop files ONLY*/
    public void pendingDeliveries(){

        if(properties_file_sys_config.getSwitchDeliveryScheduleOnAndOff().equalsIgnoreCase("ON")){
            log.info(new Date().toString()+ ": Delivery Schedule is Enabled...It can be disabled from VC Delivery Properties : \"DELIVERY_SCHEDULE_SWITCHED=OFF\" ");
            long startTime = System.currentTimeMillis();
            List<String[]> pendingDeliveryList = pendingDeliveriesService.getDistinctConsumerCodeAndRouteShortName();
            deliveryService.deliveryProcessing(pendingDeliveryList);

            long endTime = System.currentTimeMillis();
            log.info(new Date().toString()+ " : Delivery Process Took " + (endTime - startTime) + " ms");

        }else {
            log.info(new Date().toString()+ " : DELIVERY SCHEDULE IS STILL DISABLED !!! , Check VC Delivery Properties AND SWITCH to: \"ON\". ");
        }
    }
}
