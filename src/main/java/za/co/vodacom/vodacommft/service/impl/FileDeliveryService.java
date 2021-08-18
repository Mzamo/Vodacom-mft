package za.co.vodacom.vodacommft.service.impl;
/**
 * @author ncubeh on 2020/05/05
 * @package za.co.vodacom.vodacomMFT.service.impl
 */

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import za.co.vodacom.vodacommft.config.PropertiesFileSysConfig;
import za.co.vodacom.vodacommft.service.IDirectoryService;
import za.co.vodacom.vodacommft.service.IFileDeliveryService;
import za.co.vodacom.vodacommft.service.IThreadTuningService;

@Service
@RequiredArgsConstructor
public class FileDeliveryService implements IFileDeliveryService {

    final private PropertiesFileSysConfig systemCfgProperties;

    final private IDirectoryService directory_service;

    final private IThreadTuningService threadTuningService;

    @Override
    @SneakyThrows
    public void deliveryProcessing(String consumerCode, String routeShortName) {

        String localDirectory = systemCfgProperties.getLocalWorkingDirectory();
        String workDirectory = localDirectory + "running/";
        directory_service.createDeliveryWorkingDirectories(workDirectory);
        threadTuningService.doFileProcessingWithThreads(consumerCode, routeShortName, workDirectory, localDirectory);
    }
}
