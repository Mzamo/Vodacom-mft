package za.co.vodacom.vodacommft.service;

import com.jcraft.jsch.SftpException;
import org.apache.commons.compress.archivers.ArchiveException;
import za.co.vodacom.vodacommft.dto.DeliveryDetailsDTO;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IDeliveryService {

    void deliveryFileProcessing(DeliveryDetailsDTO deliveryDetails, String fileName) throws IOException, ArchiveException, SftpException, ArchiveException;
}
