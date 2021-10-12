package za.co.vodacom.vodacommft.service;

import org.apache.commons.compress.archivers.ArchiveException;
import za.co.vodacom.vodacommft.entity.sfg_cfg.DeliveryDetailsEntity;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * @author ncubeh on 2020/05/05
 * @package za.co.vodacom.vodacomMFT.service
 */

public interface ICompressService {

    String compressFile(DeliveryDetailsEntity deliveryDetails,
                        String notificationSourceFile,
                        String localDirectory,
                        String file_value_0,
                        String file_value_1,
                        String file_value_2,
                        String file_value_6,
                        String threadName,
                        BufferedWriter bw_cmp) throws IOException, ArchiveException;

    String decompressFile(String fileNameOnDisc,
                          String fileName,
                          String uncompressDir,
                          String uncompress_extension,
                          String thread_name,
                          BufferedWriter bw_cmp) throws IOException;
}
