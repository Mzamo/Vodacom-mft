package za.co.vodacom.vodacommft.service.impl;
/**
 * @author ncubeh on 2020/05/05
 * @package za.co.vodacom.vodacomMFT.service.impl
 */

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.co.vodacom.vodacommft.config.PropertiesFileSysConfig;
import za.co.vodacom.vodacommft.service.IDirectoryService;
import za.co.vodacom.vodacommft.service.IFileDeliveryService;
import za.co.vodacom.vodacommft.service.ILockService;
import za.co.vodacom.vodacommft.service.IThreadTuningService;

import java.util.List;

@Slf4j
@Service
public class FileDeliveryService implements IFileDeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(FileDeliveryService.class);

    @Autowired
    private PropertiesFileSysConfig systemCfgProperties;

    @Autowired
    private  IDirectoryService directory_service;

    @Autowired
    private ILockService lockService;

    @Autowired
    private IThreadTuningService threadTuningService;


    @Override
    @SneakyThrows
    public void deliveryProcessing(List<String[]> pendingDeliveryList) {

        try {
            String localDirectory = systemCfgProperties.getLocalWorkingDirectory();
            String workDirectory = localDirectory + "running/";
            directory_service.createDeliveryWorkingDirectories(workDirectory);

            if (pendingDeliveryList != null && pendingDeliveryList.size() > 0) {
                for (String[] pendingDelivery : pendingDeliveryList) {
                    String consumerCode = pendingDelivery[0];
                    String routeShortName = pendingDelivery[1];
                    try {
                        if (lockService.addLock(consumerCode)) {
                            threadTuningService.doFileProcessing(consumerCode, routeShortName, workDirectory, localDirectory);
                        }
                    } catch (Exception e) {
                        log.error("Failed to process Consumer Code & Route Short Name [{}/{}/{}] with the following error message", consumerCode, routeShortName, e);
                    } finally {
                        lockService.releaseLock(consumerCode);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error while calling file processing logic :- ", e);
        }
    }

    /*private void doDeliveryWithTenThreads(List<String[]> pendingDeliveryList, String workDirectory, String localDirectory) {
        try {
            if (threadPoolExecutorTen == null || threadPoolExecutorTen.isShutdown()) threadPoolExecutorTen = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(pendingDeliveryList.size());
            for (String[] pendingDelivery : pendingDeliveryList) {
                threadPoolExecutorTen.execute(() -> {
                    String consumerCode = pendingDelivery[0];
                    String routeShortName = pendingDelivery[1];
                    try {
                        if (lockService.addLock(consumerCode)) {
                            threadTuningService.doFileProcessing(consumerCode, routeShortName, workDirectory, localDirectory);
                        }
                    } catch (Exception e) {
                        log.error("Failed to process Consumer Code & Route Short Name [{}/{}/{}] with the following error message", consumerCode, routeShortName, e);
                    } finally {
                        lockService.releaseLock(consumerCode);
                    }
                    latch.countDown();
                });
            }
            latch.await();
        }catch (InterruptedException e) {
            log.error("Exception while processing consumer code", e);
        } finally {
            if(!threadPoolExecutorTen.isShutdown())threadPoolExecutorTen.shutdown();
        }
    }*/
}
