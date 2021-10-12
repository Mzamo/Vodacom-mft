package za.co.vodacom.vodacommft.service.impl;
/**
 * @author ncubeh on 2020/05/05
 * @package za.co.vodacom.vodacomMFT.service.impl
 */

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.co.vodacom.vodacommft.config.PropertiesFileSysConfig;
import za.co.vodacom.vodacommft.dto.DeliveryDetailsDTO;
import za.co.vodacom.vodacommft.entity.sfg_cfg.DeliveryDetailsEntity;
import za.co.vodacom.vodacommft.entity.sfg_rpt.PendingDeliveriesEntity;
import za.co.vodacom.vodacommft.repository.sfg_cfg.DeliveryDetailsRepository;
import za.co.vodacom.vodacommft.repository.sfg_rpt.DeliveredFileEntityRepository;
import za.co.vodacom.vodacommft.repository.sfg_rpt.PendingDeliveriesRepository;
import za.co.vodacom.vodacommft.service.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class DeliveryService implements IDeliveryService {

    private boolean changePermissionCheck = false;

    @Autowired
    IDeliveryDetailsService deliveryDetailsService;

    @Autowired
    IPendingDeliveriesService pendingDeliveriesService;

    @Autowired
    private IFtpClientService ftpClientService;

    @Autowired
    private ICompressService compressService;

    @Autowired
    private ISftpClientService sftpClientService;

    @Autowired
    private PropertiesFileSysConfig propertiesConfig;

    @Autowired
    IRenameService renameService;

    @Autowired
    private IOSCopyService osCopyService;

    @Autowired
    AgeAnalysisService ageAnalysisService;

    @Autowired
    private MFTValidationService mftValidationService;

    @Autowired
    private DirectoryService directory_service;

    @Autowired
    private PendingDeliveriesRepository pendingDeliveriesRepository;

    @Autowired
    private DeliveryDetailsRepository delivery_details_repo;

    @Autowired
    private DeliveredFileEntityRepository delivered_file_entity_repo;


    @Override
    public void deliveryFileProcessing(DeliveryDetailsDTO deliveryDetails, String fileName) throws IOException, ArchiveException, SftpException {

        BufferedWriter buff_buff = null;
        String threadName = Thread.currentThread().getName();
        try {
            buff_buff = new BufferedWriter(new FileWriter(deliveryDetails.getLocal_working_dir() +  threadName  +  ".log", true));
            buff_buff.newLine();

            buff_buff.write( LocalDateTime.now() + ": Thread.Name :-  " + threadName + " processing the file : " + fileName);
            buff_buff.newLine();

            String protocol = deliveryDetails.getPendingDeliveriesEntity().getConsumerProtocol();
            switch (protocol) {
                case "FTP": {
                    buff_buff.write(LocalDateTime.now() + " :Remote FTP Server :- " + deliveryDetails.getFtp_host());
                    buff_buff.newLine();

                    FTPClient ftpClient = null;
                    FTPSClient ftpsClient = null;
                    try {
                        String ftpssl = deliveryDetails.getFtp_ssl();
                        if (!"SSL_NONE".equalsIgnoreCase(ftpssl)) {
                            ftpsClient = ftpClientService.getFTPSClient(ftpssl);
                            ftpsClient.execPROT(deliveryDetails.getFtp_prot_level());
                        } else {
                            ftpClient = new FTPClient();
                        }
                        String ftp_host = deliveryDetails.getPendingDeliveriesEntity().getConsumerHost();
                        int ftp_port = deliveryDetails.getPendingDeliveriesEntity().getFtpPort();
                        String ftp_user = deliveryDetails.getPendingDeliveriesEntity().getConsumerUserName();
                        String ftp_password = deliveryDetails.getPendingDeliveriesEntity().getConsumerPassword();

                        FTPClient ftp_client_conn = ftpClientService.openFtpConnection(ftpClient, ftp_host, ftp_port, ftp_user, ftp_password);
                        String quoteCommand = deliveryDetails.getDeliveryDetailsEntity().getQuoteCommand();
                        if (!isEmpty(quoteCommand) && !quoteCommand.toLowerCase().contains("ull")) {
                            ftpClientService.ftpQuoteCommand(ftp_client_conn, quoteCommand);
                        }

                        String transferType = deliveryDetails.getFtp_transfer_type();
                        if (!isEmpty(transferType)) {
                            ftpClientService.ftpTransferType(ftp_client_conn, transferType);
                        }

                        String connectionType = deliveryDetails.getFtp_connection_type();
                        if (!isEmpty(connectionType)) {
                            ftpClientService.ftpConnectionType(ftp_client_conn, connectionType);
                        }

                        if (ftp_client_conn.isConnected()) {
                            int ftp_processed_file_count = ftpDeliveryFileProcessing(ftp_client_conn, deliveryDetails, fileName, threadName, buff_buff);
                            buff_buff.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :-  Total Sftp Files files Delivered = > " + ftp_processed_file_count +
                                    "\n  For Consumer :=>  " + deliveryDetails.getPendingDeliveriesEntity().getConsumerCode() +
                                    "\n For RoutShortName : => " + deliveryDetails.getPendingDeliveriesEntity().getRouteShortName());
                            buff_buff.newLine();

                            //lockService.releaseLock(delivery_details_process.getDeliveryDetailsEntity().getConsumerCode());
                        }

                    } catch (Exception ftp_ex) {
                        log.error("Thread.Name :-  " + threadName + " :- SANGOMA ON FTP PROTOCOL !!! Error, something wrong happened.....\n" + ftp_ex.getMessage());
                        buff_buff.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :- SANGOMA ON FTP PROTOCOL !!! Error, something wrong happened.....\n" + ftp_ex.getMessage());
                        buff_buff.newLine();
                    } finally {

                        ftpClientService.closeFtpConnection(ftpsClient);
                        ftpClientService.closeFtpConnection(ftpsClient);
                        ftpClient = null; ftpsClient = null;
                    }
                    break;
                }

                case "SFTP": {
                    ChannelSftp sftpClient = null;
                    try {
                        log.info("Thread.Name :-  " + threadName + " :Get SFTP Connection Details For Consumer:- " + deliveryDetails.getConsumerCode());
                        buff_buff.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :Get SFTP Connection Details For Consumer:- " + deliveryDetails.getConsumerCode());
                        buff_buff.newLine();
                        System.out.println("===================> deliveryDetails.getConsumerCode() = " + deliveryDetails.getConsumerCode());
                        String preferredAuth = deliveryDetails.getSftpProfDetailsEntity().getPreferredAuth();
                        System.out.println("=============> preferredAuth = " + preferredAuth);
                        boolean passwordAuth = "PASSWORD".equalsIgnoreCase(preferredAuth);
                        buff_buff.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " : Password Authentication :- " + passwordAuth);
                        buff_buff.newLine();

                        String userName = deliveryDetails.getSftpProfDetailsEntity().getRemote_user();
                        System.out.println("===============  userName = " + userName);
                        String password = deliveryDetails.getPendingDeliveriesEntity().getConsumerPassword();
                        System.out.println("==============> password = " + password);
                        String hostName = deliveryDetails.getSftpProfDetailsEntity().getRemote_host();
                        System.out.println("==============> hostName = " + hostName);
                        int portNumber = deliveryDetails.getSftpProfDetailsEntity().getRemote_port();
                        System.out.println("==============> portNumber = " + portNumber);
                        String publicKey = deliveryDetails.getPublicKeyFile();
                        System.out.println("=============> publicKey = " + publicKey);

                        sftpClient = sftpClientService.sftpDelLogIn(preferredAuth, userName, password, hostName, portNumber, publicKey);

                        if (sftpClient != null && sftpClient.isConnected() ) {
                            int sftp_processed_files = sftpDeliveryFileProcessing(sftpClient, deliveryDetails, fileName, threadName, buff_buff);
                            buff_buff.write(LocalDateTime.now() + ": Thread.Name  " + threadName + "  :Total Sftp Files files Delivered = > " + sftp_processed_files +
                                    "\n  For Consumer :=>  " + deliveryDetails.getPendingDeliveriesEntity().getConsumerCode() +
                                    "\n For RoutShortName : => " + deliveryDetails.getPendingDeliveriesEntity().getRouteShortName());
                            buff_buff.newLine();
                        } else {
                            System.out.println("=============== SFTPCLIENT  is not connected Please Check !!!!= ");
                        }

                    } catch (Exception sftp_ex) {
                        log.error( threadName + " : SANGOMA IN SFTP PROTOCOL !!! Error , Something wrong happened. Consumer is : " +  sftp_ex.getMessage());
                        buff_buff.write(LocalDateTime.now() + ": " + threadName + " : SANGOMA IN SFTP PROTOCOL !!! Error , Something wrong happened. Consumer is : " + deliveryDetails.getConsumerCode() + ",  " + sftp_ex.getMessage());
                        buff_buff.newLine();
                        sftp_ex.printStackTrace();
                    } finally {

                        sftpClientService.sftpLogOut(sftpClient, buff_buff);
                        buff_buff.write(new Date().toString() + ": Thread.Name :-  " + threadName + " :Closing SFTP Client Connections <<sftp_client>>.....");
                        buff_buff.newLine();
                    }
                    break;
                }

                case "OSCOPY":
                    log.info( ": Thread.Name :-  " + threadName + " :CONSUMER_PROTOCOL ");
                    buff_buff.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :CONSUMER_PROTOCOL ");
                    buff_buff.newLine();

                    int oscopy_proc_files = osCopyDeliveryFileProcessing(deliveryDetails, fileName, threadName, buff_buff);
                    buff_buff.write(new Date().toString() + " : Thread.Name :-  " + threadName + " :- Total Sftp Files files Delivered = > " + oscopy_proc_files +
                            "\n  For Consumer :=>  " + deliveryDetails.getPendingDeliveriesEntity().getConsumerCode()
                            + "\n For RoutShortName : => " + deliveryDetails.getPendingDeliveriesEntity().getRouteShortName());
                    buff_buff.newLine();

                    break;

                default:

                    buff_buff.write(new Date().toString() + "  SANGOMA !!!: NO DELIVERY PROTOCOL SPECIFIED !!!" +
                            "\n  For Consumer :=>  " + deliveryDetails.getPendingDeliveriesEntity().getConsumerCode()
                            + "\n For RoutShortName : => " + deliveryDetails.getPendingDeliveriesEntity().getRouteShortName());
                    buff_buff.newLine();

                    break;
            }

        } finally {
            if (buff_buff != null) buff_buff.close();
        }
    }

    private int ftpDeliveryFileProcessing(FTPClient ftp_client, DeliveryDetailsDTO deliveryDetails, String file_metadata, String threadName, BufferedWriter buff_buff) throws IOException, ArchiveException, SftpException {

        String ftpHost = deliveryDetails.getFtp_host();
        String remoteHost = deliveryDetails.getSftpProfDetailsEntity().getRemote_host();
        PendingDeliveriesEntity pendingDelivery = deliveryDetails.getPendingDeliveriesEntity();
        String changePermission = deliveryDetails.getDeliveryDetailsEntity().getChangeFilePermissions();
        String consumer_code = pendingDelivery.getConsumerCode();

        List<String> ftp_age_analysis_list = new ArrayList<>();
        String ftp_remote_consumer_dir = getRemoteDirectory(pendingDelivery.getConsumerRemoteDir(), pendingDelivery.getConsumerProtocol(), ftpHost, remoteHost);
        String date_to_file_append = getDateFormat();

        changePermissionCheck = "yes".equalsIgnoreCase(changePermission);
        String notificationFileExtension = "";
        String notificationFileName = "";
        String existingExtension = "";

        String notify_ext = getNotificationFileExt(deliveryDetails.getDeliveryDetailsEntity().getNotificationFileExt());
        if (!notify_ext.isEmpty()) {
            notificationFileExtension = notify_ext.substring(1, notify_ext.length());
        }

        int ftp_count_processed_files = 0;
        int ftp_error_count = 0;
        String ftp_delivery_status = "33";
        int ftp_tmp_error_count = 0;

        String notificationSourceFile = propertiesConfig.getNotificationSourceFile();
        String localDirectory = deliveryDetails.getLocal_working_dir();
        String[] ftp_files_tobe_delivered_array = file_metadata.split(";");
        String protocol = deliveryDetails.getPendingDeliveriesEntity().getConsumerProtocol();
        String newPermissionString = deliveryDetails.getDeliveryDetailsEntity().getNewPermissionString();
        String[] ftp_route_metaData = new String[6];;

        if (!doesSFGFilExist(ftp_files_tobe_delivered_array[6])) {

            System.out.println("Thread:" + threadName + new Date().toString() + ">>>> File Name: " + ftp_files_tobe_delivered_array[6] + "for Consumer_Code:-->"+consumer_code +" < DOES NOT EXIST IN FILE SYSTEM, Lets Continue to the Next Files.......");
            buff_buff.write("Thread:" + threadName + new Date().toString() + " >>>> File Name: " + ftp_files_tobe_delivered_array[6] + "for Consumer_Code:-->"+consumer_code +" < DOES NOT EXIST IN FILE SYSTEM, Lets Continue to the Next Files......."); // + " ftp command " + listOfFilenames[i]);
            buff_buff.newLine();
            ftp_delivery_status = "92";
            System.out.println(new Date().toString() + "Thread:" + threadName + " :- >>>>>>> Check UpdateStatus Value:- " + ftp_delivery_status);

        }else {

        // for (String file_processing_options : getFileProcessingOptions(deliveryDetails.getDeliveryDetailsEntity(), threadName, buff_buff)) {

        Map<String, String> file_processing_options = getFileProcessingOptions(deliveryDetails.getDeliveryDetailsEntity(), threadName, buff_buff);
        if ("compress".equalsIgnoreCase(file_processing_options.get("compress"))) {
            String[] ftp_compressed_file_values = compressService.compressFile(deliveryDetails.getDeliveryDetailsEntity(),
                    notificationSourceFile,
                    localDirectory,
                    ftp_files_tobe_delivered_array[0],
                    ftp_files_tobe_delivered_array[1],
                    ftp_files_tobe_delivered_array[2],
                    ftp_files_tobe_delivered_array[6],
                    threadName,
                    buff_buff).split(";");
            ftp_files_tobe_delivered_array[2] = ftp_compressed_file_values[0];
            ftp_files_tobe_delivered_array[6] = ftp_compressed_file_values[1];
        }

        if ("uncompress".equalsIgnoreCase(file_processing_options.get("uncompress"))) {
            String uncompressedDirectory = localDirectory + ftp_files_tobe_delivered_array[0] + "/";
            compressService.decompressFile(ftp_files_tobe_delivered_array[1],
                    ftp_files_tobe_delivered_array[2],
                    uncompressedDirectory,
                    deliveryDetails.getDeliveryDetailsEntity().getUncompressType(),
                    threadName,
                    buff_buff);
        }


        if ("rename".equalsIgnoreCase(file_processing_options.get("rename"))) {
            //Rename a file before delivering
            String filePattern = deliveryDetails.getDeliveryDetailsEntity().getNewFileNamePattern();
            ftp_files_tobe_delivered_array[2] = renameAFile(ftp_files_tobe_delivered_array[2], filePattern);
        }

        if ("remove".equalsIgnoreCase(file_processing_options.get("remove"))) {
            //Remove characters
            removeCharsFromAFile(ftp_files_tobe_delivered_array, localDirectory, deliveryDetails.getDeliveryDetailsEntity().getRemoveChars());
        }

        // }


            if (getRemoteAction(deliveryDetails.getDeliveryDetailsEntity().getDestFileAction()).equalsIgnoreCase("RENAME")) {
                ftp_client.rename(ftp_remote_consumer_dir + ftp_files_tobe_delivered_array[2], ftp_remote_consumer_dir + ftp_files_tobe_delivered_array[2] + date_to_file_append);
            }

            String deliver_with_temp_name = deliveryDetails.getDeliveryDetailsEntity().getUseTempName();
            String permissions = getChmodDetails(deliveryDetails.getDeliveryDetailsEntity().getNewPermissionString());
            if (getDeliverWithTempName(deliver_with_temp_name).equalsIgnoreCase("yes")) {

                try {

                    ftp_client.deleteFile(ftp_files_tobe_delivered_array[2]);
                    InputStream file_in_stream = new FileInputStream(ftp_files_tobe_delivered_array[6]);
                    ftp_client.storeFile(ftp_files_tobe_delivered_array[0], file_in_stream);

                } catch (IOException fd) {
                    log.error(": Thread.Name :-  " + threadName + " :- FTP PUT error occurred. This is fatal. " + fd.getMessage() + ", Consumer is : " + deliveryDetails.getConsumerCode());
                    buff_buff.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :- FTP PUT error occurred. This is fatal. " + fd.getMessage() + ", Consumer is : " + deliveryDetails.getConsumerCode());
                    buff_buff.newLine();
                    ftp_delivery_status = "92";
                    if (ftp_tmp_error_count == 0) {
                        if (doesSFGFilExist(localDirectory + ftp_files_tobe_delivered_array[0])) {
                            deleteTempDirs(localDirectory, ftp_files_tobe_delivered_array[0]);
                        }
                    }
                }

                if (!permissions.isEmpty() && changePermissionCheck) {
                    consumerProtocolChangePermissions(ftp_client, null, ftp_remote_consumer_dir + ftp_files_tobe_delivered_array[0], permissions, protocol, newPermissionString);
                }
                ftp_client.rename(ftp_files_tobe_delivered_array[0], ftp_files_tobe_delivered_array[2]);

            } else {

                InputStream file_input_stream = new FileInputStream(ftp_files_tobe_delivered_array[6]);
                try {
                    ftp_client.storeFile(ftp_files_tobe_delivered_array[2], file_input_stream);
                } catch (Exception ftp_clt) {
                    if (doesSFGFilExist(localDirectory + ftp_files_tobe_delivered_array[0])) {
                        deleteTempDirs(localDirectory, ftp_files_tobe_delivered_array[0]);
                    }
                    log.error(new Date().toString() + ": Thread.Name :-  " + threadName + " :-  FTP PUT Error occurred On FTP DeliverWithoutTemName!!!. This is fatal. " + ftp_clt.getMessage());
                }
                if (!permissions.isEmpty() && changePermissionCheck) {
                    consumerProtocolChangePermissions(ftp_client, null, ftp_files_tobe_delivered_array[2], permissions, protocol, newPermissionString);
                }
            }

            if (!notificationFileExtension.isEmpty()) {
                String tempFileName[] = ftp_files_tobe_delivered_array[2].split("\\.");
                existingExtension = tempFileName[tempFileName.length - 1];
                notificationFileName = ftp_files_tobe_delivered_array[2].replace(existingExtension, notificationFileExtension);
                try {
                    try (InputStream inputStream = new FileInputStream(notificationSourceFile)) {
                        ftp_client.storeFile(notificationFileName, inputStream);
                    }
                } catch (Exception uploadEx) {
                    ftp_delivery_status = "92";
                    if (doesSFGFilExist(localDirectory + ftp_files_tobe_delivered_array[0])) {
                        deleteTempDirs(localDirectory, ftp_files_tobe_delivered_array[0]);
                    }
                }
            }

            ftp_route_metaData = ftp_files_tobe_delivered_array[4].split(",");
            if (deliveryDetails.getDeliveryDetailsEntity().getAgeAnalysis().equalsIgnoreCase("Yes") && ftp_delivery_status.equalsIgnoreCase("33")) {
                ftp_age_analysis_list.add("y;" + Integer.parseInt(ftp_files_tobe_delivered_array[0]) + ";" + ftp_files_tobe_delivered_array[6] + ";" + ftp_files_tobe_delivered_array[2] + ";" + ftp_files_tobe_delivered_array[2] + ";"
                        + ftp_route_metaData[0] + ";" + ftp_route_metaData[2] + ";" + ftp_files_tobe_delivered_array[5] + ";" + ftp_files_tobe_delivered_array[3]
                        + "," + ftp_files_tobe_delivered_array[4] + ";n;1;0;" + new Date().getTime() + ";33;FTP");

            }

        }
        Date delivery_date = new Date(System.currentTimeMillis());
        if (ftp_delivery_status.equalsIgnoreCase("33")) {
            ftp_count_processed_files++;
            deletePendingDeliveriesFilesByPuid(ftp_files_tobe_delivered_array[0]);
            updateSuccessDeliveredFileTsDeliverStatusAndKeyByDeliverUid(delivery_date, ftp_delivery_status, ftp_route_metaData[2], Integer.parseInt(ftp_files_tobe_delivered_array[5]));

        }else {
            //TODO To Fix this, call correct Method() :- updateFailedDeliveredFileTsStatusAndKeyByUid
            deletePendingDeliveriesFilesByPuid(ftp_files_tobe_delivered_array[0]);
            delivery_date = null;
            updateFailedDeliveredFileTsStatusAndKeyByUid(ftp_route_metaData[2], Integer.parseInt(ftp_files_tobe_delivered_array[5]));
        }

        /*Remember we created temp Directory for compression, The Directory name is PD_UID [0] inside LOCAL working Dir
         * LETS MAKE THINGS WORK and BEAUTIFY LATER... YOU FREE TO DO IT BETTER
         *
         * */
        if (deliveryDetails.getDeliveryDetailsEntity().getUseCompress().equalsIgnoreCase("yes") && doesSFGFilExist(localDirectory + ftp_files_tobe_delivered_array[0])){
            directory_service.deleteNonEmptyDirectoryUsingStream(localDirectory + ftp_files_tobe_delivered_array[0]);
        }

        if(Math.round((double)ftp_error_count / 1 * 100) >= deliveryDetails.getDeliveryDetailsEntity().getDeliveryFailureRate()) {
            if(autoSuspendDelivery(deliveryDetails.getConsumerCode(), deliveryDetails.getRouteShortName())){
                log.info(new Date().toString() + ": Thread.Name :-  " + threadName + " :- Delivery auto-suspended successfully.");
                buff_buff.write(new Date().toString() + ": Thread.Name :-  " + threadName + " :- Delivery auto-suspended successfully.");
                buff_buff.newLine();
            }
        }

        if (ftp_age_analysis_list.size() > 0) {
            for (String age_analysis_listToProcess : ftp_age_analysis_list) {
                ageAnalysisService.ageAnalyseFileProcessing(age_analysis_listToProcess, deliveryDetails.getPendingDeliveriesEntity().getRouteShortName());
            }
        }
        return ftp_count_processed_files;
        }


    //These Methods will be generic, am tired now, ...
    private int sftpDeliveryFileProcessing(ChannelSftp sftp_client, DeliveryDetailsDTO deliveryDetails, String fileName, String threadName, BufferedWriter buff_buff) throws IOException, ArchiveException, SftpException {

        List<String> sftp_age_analysis_list = new ArrayList<>();

        String localDirectory = deliveryDetails.getLocal_working_dir();
        System.out.println("==============> SFTP localDirectory = " + localDirectory);

        String protocol = deliveryDetails.getPendingDeliveriesEntity().getConsumerProtocol();
        String ftpHostName = deliveryDetails.getFtp_host();
        String remoteHostName = deliveryDetails.getSftpProfDetailsEntity().getRemote_host();
        String changeDirector = deliveryDetails.getSftpProfDetailsEntity().getChangeDirectory();
        String remoteDirectory = getRemoteDirectory(changeDirector, protocol, ftpHostName, remoteHostName);
        String consumer_code = deliveryDetails.getPendingDeliveriesEntity().getConsumerCode();

        String date_to_file_append = getDateFormat();
        changePermissionCheck = "yes".equalsIgnoreCase(deliveryDetails.getDeliveryDetailsEntity().getChangeFilePermissions());
        String notificationFileExtension = "";
        String notificationFileName = "";
        String existingExtension = "";

        String notify_ext = getNotificationFileExt(deliveryDetails.getDeliveryDetailsEntity().getNotificationFileExt());

        if (!notify_ext.isEmpty()) {
            notificationFileExtension = notify_ext.substring(1, notify_ext.length());
            log.info(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :-  ====> SFTP PROCESSING notificationFileExtension Value ..." + notificationFileExtension);
            buff_buff.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :-  ====> SFTP PROCESSING notificationFileExtension Value ..." + notificationFileExtension);
            buff_buff.newLine();
        }

        String notificationSourceFile = propertiesConfig.getNotificationSourceFile();
        int sftp_processed_files_count = 0;
        int sftp_error_count = 0;

        String sftp_delivery_status = "33"; //Assume successful until set to failure
        int sftp_tmp_error_count = 0;
        String[] sftp_files_tobe_delivered_array = fileName.split(";");
        String[] sftp_route_metaData = new String[6];

        String newPermissionString = deliveryDetails.getDeliveryDetailsEntity().getNewPermissionString();
        if (!doesSFGFilExist(sftp_files_tobe_delivered_array[6])) {

            System.out.println("Thread:" + threadName + new Date().toString() + ">>>> File Name: " + sftp_files_tobe_delivered_array[6] + "for SFTP Consumer_Code:-->"+consumer_code +" < DOES NOT EXIST IN FILE SYSTEM, Lets Continue to the Next Files.......");
            buff_buff.write("Thread:" + threadName + new Date().toString() + " >>>> File Name: " + sftp_files_tobe_delivered_array[6] + "for SFTP Consumer_Code:-->"+consumer_code +" < DOES NOT EXIST IN FILE SYSTEM, Lets Continue to the Next Files......."); // + " ftp command " + listOfFilenames[i]);
            buff_buff.newLine();
            sftp_delivery_status = "92";
            System.out.println(new Date().toString() + "Thread:" + threadName + " :- >>>>>>> Check UpdateStatus Value:- " + sftp_delivery_status);


        } else {

        Map<String, String> file_processing_options = getFileProcessingOptions(deliveryDetails.getDeliveryDetailsEntity(), threadName, buff_buff);
        System.out.println(" >>>>>>>>>>>>>>>>>>>> SFTP file_processing_options = " + file_processing_options);
        if ("compress".equalsIgnoreCase(file_processing_options.get("compress"))) {
            //Compress a file before delivering TODO: Herbie to check Compressed_file name
            System.out.println(" >>>>>>>>>>>>>>>>>>>> About to invoke Compression ");
            String compressFileStr = compressService.compressFile(
                    deliveryDetails.getDeliveryDetailsEntity(),
                    notificationSourceFile,
                    localDirectory,
                    sftp_files_tobe_delivered_array[0],
                    sftp_files_tobe_delivered_array[1],
                    sftp_files_tobe_delivered_array[2],
                    sftp_files_tobe_delivered_array[6],
                    threadName,
                    buff_buff);

            if (compressFileStr != null && compressFileStr.contains(";")) {
                String[] sftp_compressed_file_values = compressFileStr.split(";");
                sftp_files_tobe_delivered_array[2] = sftp_compressed_file_values[0];
                sftp_files_tobe_delivered_array[6] = sftp_compressed_file_values[1];
            }

        }

        if ("uncompress".equalsIgnoreCase(file_processing_options.get("uncompress"))) {
            //Uncompress a file before delivering
            String[] sftp_uncompressed_file_values = uncompressAFile(localDirectory,
                    sftp_files_tobe_delivered_array[0],
                    sftp_files_tobe_delivered_array[1],
                    sftp_files_tobe_delivered_array[2],
                    sftp_files_tobe_delivered_array[6],
                    deliveryDetails.getDeliveryDetailsEntity().getUncompressType(),
                    threadName,
                    buff_buff).split(";");
            sftp_files_tobe_delivered_array[2] = sftp_uncompressed_file_values[0];
            sftp_files_tobe_delivered_array[6] = sftp_uncompressed_file_values[1];
        }
        /*if ("rename".equalsIgnoreCase(file_processing_options.get("rename"))) {
            String filePattern = deliveryDetails.getDeliveryDetailsEntity().getNewFileNamePattern();
            sftp_files_tobe_delivered_array[2] = renameAFile(sftp_files_tobe_delivered_array[2], filePattern);
        }*/

        if ("remove".equalsIgnoreCase(file_processing_options.get("remove"))) {
            //Remove characters
            removeCharsFromAFile(sftp_files_tobe_delivered_array, localDirectory, deliveryDetails.getDeliveryDetailsEntity().getRemoveChars());
        }


        if (getRemoteAction(deliveryDetails.getDeliveryDetailsEntity().getDestFileAction()).equalsIgnoreCase("RENAME")) {
            if (mftValidationService.checkSftpFileExists(sftp_client, remoteDirectory + sftp_files_tobe_delivered_array[2])) {
                sftp_client.rename(remoteDirectory + sftp_files_tobe_delivered_array[2], remoteDirectory + sftp_files_tobe_delivered_array[2] + date_to_file_append);
            }
        }

            String deliver_with_temp_name = deliveryDetails.getDeliveryDetailsEntity().getUseTempName();
            String permissions = getChmodDetails(deliveryDetails.getDeliveryDetailsEntity().getNewPermissionString());
            if (getDeliverWithTempName(deliver_with_temp_name).equalsIgnoreCase("yes")) {
                try {
                    if (mftValidationService.checkSftpFileExists(sftp_client, remoteDirectory + sftp_files_tobe_delivered_array[2])) {
                        sftp_client.rm(remoteDirectory + sftp_files_tobe_delivered_array[2]);
                    }
                    sftp_client.put(sftp_files_tobe_delivered_array[6], remoteDirectory + sftp_files_tobe_delivered_array[0]);
                } catch (SftpException fd) {
                    log.error(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :- SFTP PUT error occurred. This is fatal." + fd.getMessage() + ", For Consumer is : " + deliveryDetails.getConsumerCode());
                    sftp_delivery_status = "92";
                    if (sftp_tmp_error_count == 0) {
                        sftp_error_count++;

                        if (doesSFGFilExist(localDirectory + sftp_files_tobe_delivered_array[0])) {
                            deleteTempDirs(localDirectory, sftp_files_tobe_delivered_array[0]);
                        }

                    }
                }

                if (!permissions.isEmpty() && changePermissionCheck) {

                    boolean file_exist_value = mftValidationService.checkSftpFileExists(sftp_client, remoteDirectory + sftp_files_tobe_delivered_array[0]);
                    if (file_exist_value) {
                        consumerProtocolChangePermissions(null, sftp_client, remoteDirectory + sftp_files_tobe_delivered_array[0], "", protocol, newPermissionString);
                        if (mftValidationService.checkSftpFileExists(sftp_client, remoteDirectory + sftp_files_tobe_delivered_array[0])) {
                            try {
                                sftp_client.rename(remoteDirectory + sftp_files_tobe_delivered_array[0], remoteDirectory + sftp_files_tobe_delivered_array[2]);

                            } catch (SftpException re) {
                                log.error(": Thread.Name :-  " + threadName + " :-SANGOMA !!! When Renaming SFTP delivery file After CHMOD :: Deliver with TempName From, " + sftp_files_tobe_delivered_array[0] + "  :TO: " + sftp_files_tobe_delivered_array[2] +
                                        " :: Could not be Named " + re.getMessage() + ", Consumer is : " + deliveryDetails.getConsumerCode());

                                sftp_delivery_status = "92";
                                if (sftp_tmp_error_count == 0) {
                                    if (Math.round((double) (sftp_error_count + 1) / 1 * 100) >= deliveryDetails.getDeliveryDetailsEntity().getDeliveryFailureRate()) { //Still not reached failure limit so continue to fail files
                                        if (doesSFGFilExist(localDirectory + sftp_files_tobe_delivered_array[0])) {
                                            deleteTempDirs(localDirectory, sftp_files_tobe_delivered_array[0]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } else {

                try {
                    sftp_client.put(sftp_files_tobe_delivered_array[6], remoteDirectory + sftp_files_tobe_delivered_array[2]);

                } catch (SftpException sftpPutEx) {
                    log.error(": Thread.Name :-  " + threadName + " :- SFTP Put with final name returned error. ... CONSUMER_CODE is :- " + deliveryDetails.getConsumerCode() + " :: ROUTE_SHORT_NAME:- " + deliveryDetails.getRouteShortName() + ", " + sftpPutEx.getMessage());
                    sftp_delivery_status = "92";
                    if (sftp_tmp_error_count == 0) {
                        if (Math.round((double) (sftp_error_count + 1) / 1 * 100) >= deliveryDetails.getDeliveryDetailsEntity().getDeliveryFailureRate()) { //Still not reached failure limit so continue to fail files
                            if (doesSFGFilExist(localDirectory + sftp_files_tobe_delivered_array[0])) {
                                deleteTempDirs(localDirectory, sftp_files_tobe_delivered_array[0]);
                            }
                        }
                    }
                }

                if (!permissions.equals("") && changePermissionCheck) {
                    if (mftValidationService.checkSftpFileExists(sftp_client, remoteDirectory + sftp_files_tobe_delivered_array[2])) {
                        consumerProtocolChangePermissions(null, sftp_client, remoteDirectory + sftp_files_tobe_delivered_array[2], "", protocol, newPermissionString);
                    }
                }
            }

            if (!notificationFileExtension.isEmpty()) {
                String tempFileName[] = sftp_files_tobe_delivered_array[2].split("\\.");
                existingExtension = tempFileName[tempFileName.length - 1];
                notificationFileName = sftp_files_tobe_delivered_array[2].replace(existingExtension, notificationFileExtension);

                try {
                    sftp_client.put(notificationSourceFile, remoteDirectory + notificationFileName);
                } catch (SftpException sftpPutEx) {
                    log.error(new Date().toString() + ": Thread.Name :-  " + threadName + " :- SFTP notification file PUT returned error... CONSUMER_CODE is :- " + deliveryDetails.getConsumerCode() + " :: ROUTE_SHORT_NAME:- " + deliveryDetails.getRouteShortName() + ", " + sftpPutEx.getMessage());
                    sftp_delivery_status = "92";
                    if (Math.round((double) (sftp_error_count + 1) / 1 * 100) >= deliveryDetails.getDeliveryDetailsEntity().getDeliveryFailureRate()) { //Still not reached failure limit so continue to fail files
                        if (doesSFGFilExist(localDirectory + sftp_files_tobe_delivered_array[0])) {
                            deleteTempDirs(localDirectory, sftp_files_tobe_delivered_array[0]);
                        }
                    }
                }

                if (changePermissionCheck) {
                    if (mftValidationService.checkSftpFileExists(sftp_client, remoteDirectory + notificationFileName)) {
                        consumerProtocolChangePermissions(null, sftp_client, remoteHostName + notificationFileName, "", protocol, newPermissionString);
                    }
                }
            }

            //TODO :- Updated Delivered Table for Successfull delivered files
            sftp_route_metaData = sftp_files_tobe_delivered_array[4].split(",");

            if (deliveryDetails.getDeliveryDetailsEntity().getAgeAnalysis().equalsIgnoreCase("Yes") && sftp_delivery_status.equalsIgnoreCase("33")) {
                sftp_age_analysis_list.add("y;" + Integer.parseInt(sftp_files_tobe_delivered_array[0]) + ";" + sftp_files_tobe_delivered_array[6] + ";" + sftp_files_tobe_delivered_array[2] + ";" + sftp_files_tobe_delivered_array[2] + ";"
                        + sftp_route_metaData[0] + ";" + sftp_route_metaData[2] + ";" + sftp_files_tobe_delivered_array[5] + ";" + sftp_files_tobe_delivered_array[3]
                        + "," + sftp_files_tobe_delivered_array[4] + ";n;1;0;" + new Date().getTime() + ";33;FTP");

            }
        }

        Date delivery_date = new Date(System.currentTimeMillis());
        if (sftp_delivery_status.equalsIgnoreCase("33")) {
            sftp_processed_files_count++;
            deletePendingDeliveriesFilesByPuid(sftp_files_tobe_delivered_array[0]);
            updateSuccessDeliveredFileTsDeliverStatusAndKeyByDeliverUid(delivery_date, sftp_delivery_status, sftp_route_metaData[2], Integer.parseInt(sftp_files_tobe_delivered_array[5]));
        }else {
            //TODO To Fix this, call correct Method() :- updateFailedDeliveredFileTsStatusAndKeyByUid
            deletePendingDeliveriesFilesByPuid(sftp_files_tobe_delivered_array[0]);
            delivery_date = null;
            updateFailedDeliveredFileTsStatusAndKeyByUid(sftp_route_metaData[2], Integer.parseInt(sftp_files_tobe_delivered_array[5]));
        }

        /*Remember we created temp Directory for compression, The Directory name is PD_UID [0] inside LOCAL working Dir
         * LETS MAKE THINGS WORK and BEAUTIFY LATER... YOU FREE TO DO IT BETTER
         *
         * */
        if (deliveryDetails.getDeliveryDetailsEntity().getUseCompress().equalsIgnoreCase("yes") && doesSFGFilExist(localDirectory+ sftp_files_tobe_delivered_array[0])){
            directory_service.deleteNonEmptyDirectoryUsingStream(localDirectory+ sftp_files_tobe_delivered_array[0]);
        }


        if (sftp_age_analysis_list.size() > 0) {
            for (String age_analysis_listToProcess : sftp_age_analysis_list) {
                ageAnalysisService.ageAnalyseFileProcessing(age_analysis_listToProcess, deliveryDetails.getPendingDeliveriesEntity().getRouteShortName());
            }
        }

        if(Math.round((double)sftp_error_count / 1 * 100) >= deliveryDetails.getDeliveryDetailsEntity().getDeliveryFailureRate()){
            autoSuspendDelivery(deliveryDetails.getConsumerCode(), deliveryDetails.getRouteShortName());
        }
        return sftp_processed_files_count;
    }


    /*These Methods will be generic, am tired now, ... One method with a Switch*/
    private int osCopyDeliveryFileProcessing(DeliveryDetailsDTO deliveryDetails, String fileName, String threadName, BufferedWriter buff_buff) throws IOException, ArchiveException, SftpException {

        String date_to_file_append = getDateFormat();
        changePermissionCheck = "yes".equalsIgnoreCase(deliveryDetails.getDeliveryDetailsEntity().getChangeFilePermissions());

        String notificationFileExtension = "";
        String notificationFileName = "";
        String existingExtension = "";

        String notify_ext = getNotificationFileExt(deliveryDetails.getDeliveryDetailsEntity().getNotificationFileExt());
        if (!notify_ext.isEmpty()) {
            notificationFileExtension = notify_ext.substring(1, notify_ext.length());
        }
        List<String> oscopy_age_analysis_list = new ArrayList<>();
        int oscopy_proc_files_count = 0;
        int oscopy_error_count = 0;

        buff_buff.write(":: => " + LocalDateTime.now() + " : Thread.Name :-  " + threadName + " :- In OSCOPY PROTOCOL Processing OF FILES : => ");
        buff_buff.newLine();
        String os_copy_delivery_status = "33";
        int oscopy_tmp_error_count = 0;
        String[] oscopy_files_tobe_delivered_array = fileName.split(";");

        String notificationSourceFile = propertiesConfig.getNotificationSourceFile();
        String localDirectory = deliveryDetails.getLocal_working_dir();
        String protocol = deliveryDetails.getPendingDeliveriesEntity().getConsumerProtocol();
       /* String ftpHostName = deliveryDetails.getFtp_host();
        String remoteHostName1 = deliveryDetails.getSftpProfDetailsEntity().getRemote_host();*/
        String changeDirector = deliveryDetails.getPendingDeliveriesEntity().getConsumerRemoteDir();
        String remoteDirectory = getRemoteDirectory(changeDirector, protocol, "", "");
        String newPermissionString =  deliveryDetails.getDeliveryDetailsEntity().getNewPermissionString();
       String consumer_code = deliveryDetails.getPendingDeliveriesEntity().getConsumerCode();
        String[] oscopy_route_metaData = new String[6];

        if (!doesSFGFilExist(oscopy_files_tobe_delivered_array[6])) {

            System.out.println("Thread:" + threadName + new Date().toString() + ">>>> File Name: " + oscopy_files_tobe_delivered_array[6] + "for Oscopy  Consumer_Code:-->"+consumer_code +" < DOES NOT EXIST IN FILE SYSTEM, Lets Continue to the Next Files.......");
            buff_buff.write("Thread:" + threadName + new Date().toString() + " >>>> File Name: " + oscopy_files_tobe_delivered_array[6] + "for Oscopy  Consumer_Code:-->"+consumer_code +" < DOES NOT EXIST IN FILE SYSTEM, Lets Continue to the Next Files......."); // + " ftp command " + listOfFilenames[i]);
            buff_buff.newLine();
            os_copy_delivery_status = "92";
            System.out.println(new Date().toString() + "Thread:" + threadName + " :- >>>>>>> Check UpdateStatus Value:- " + os_copy_delivery_status);


        } else {

            // for (String file_processing_options : getFileProcessingOptions(deliveryDetails.getDeliveryDetailsEntity(), threadName, buff_buff)) {
            Map<String, String> file_processing_options = getFileProcessingOptions(deliveryDetails.getDeliveryDetailsEntity(), threadName, buff_buff);
            if ("compress".equalsIgnoreCase(file_processing_options.get("compress"))) {
                String[] oscopy_compressed_file_values = compressService.compressFile(deliveryDetails.getDeliveryDetailsEntity(),
                        notificationSourceFile,
                        localDirectory,
                        oscopy_files_tobe_delivered_array[0],
                        oscopy_files_tobe_delivered_array[1],
                        oscopy_files_tobe_delivered_array[2],
                        oscopy_files_tobe_delivered_array[6],
                        threadName,
                        buff_buff).split(";");
                oscopy_files_tobe_delivered_array[2] = oscopy_compressed_file_values[0];
                oscopy_files_tobe_delivered_array[6] = oscopy_compressed_file_values[1];
            }

            if ("uncompress".equalsIgnoreCase(file_processing_options.get("uncompress"))) {
                String[] oscopy_uncompressed_file_values = uncompressAFile(localDirectory,
                        oscopy_files_tobe_delivered_array[0],
                        oscopy_files_tobe_delivered_array[1],
                        oscopy_files_tobe_delivered_array[2],
                        oscopy_files_tobe_delivered_array[6],
                        deliveryDetails.getDeliveryDetailsEntity().getUncompressType(),
                        threadName,
                        buff_buff).split(";");

                oscopy_files_tobe_delivered_array[2] = oscopy_uncompressed_file_values[0];
                oscopy_files_tobe_delivered_array[6] = oscopy_uncompressed_file_values[1];
            }
            if ("rename".equalsIgnoreCase(file_processing_options.get("rename"))) {
                //Rename a file before delivering
                renameAFile(oscopy_files_tobe_delivered_array[2], deliveryDetails.getDeliveryDetailsEntity().getNewFileNamePattern());
            }
            if ("remove".equalsIgnoreCase(file_processing_options.get("remove"))) {
                //Remove characters
                removeCharsFromAFile(oscopy_files_tobe_delivered_array, localDirectory, deliveryDetails.getDeliveryDetailsEntity().getRemoveChars());
            }


                if (getRemoteAction(deliveryDetails.getDeliveryDetailsEntity().getDestFileAction()).equalsIgnoreCase("RENAME")) {

                    Path source_file = Paths.get(remoteDirectory + oscopy_files_tobe_delivered_array[2]);
                    if (Files.exists(source_file)) {
                        osCopyService.copyToOS(remoteDirectory + oscopy_files_tobe_delivered_array[2], remoteDirectory + oscopy_files_tobe_delivered_array[2] + date_to_file_append);
                    }
                }

                String deliver_with_temp_name = deliveryDetails.getDeliveryDetailsEntity().getUseTempName();
                String permissions = getChmodDetails(deliveryDetails.getDeliveryDetailsEntity().getNewPermissionString());

                if (getDeliverWithTempName(deliver_with_temp_name).equalsIgnoreCase("yes")) {

                    Path source_file = Paths.get(remoteDirectory + oscopy_files_tobe_delivered_array[2]);
                    if (Files.exists(source_file)) Files.delete(source_file);

                    try {
                        osCopyService.copyToOS(oscopy_files_tobe_delivered_array[6], remoteDirectory + oscopy_files_tobe_delivered_array[0]);
                    } catch (IOException e) {
                        log.info(" : Thread.Name :-  " + threadName + " :- Error Rate Calculation Value :- ");
                        buff_buff.write(LocalDateTime.now() + " : Thread.Name :-  " + threadName + " :- Error Rate Calculation Value :- ");
                        buff_buff.newLine();
                        if (oscopy_tmp_error_count == 0) {
                            os_copy_delivery_status = "92";
                            if (doesSFGFilExist(localDirectory + oscopy_files_tobe_delivered_array[0])) {
                                if (doesSFGFilExist(localDirectory + oscopy_files_tobe_delivered_array[0])) {
                                    deleteTempDirs(localDirectory, oscopy_files_tobe_delivered_array[0]);
                                }
                            }
                        }
                    }

                    //check for permissions
                    if (!permissions.equals("") && changePermissionCheck) {
                        consumerProtocolChangePermissions(null, null, remoteDirectory + oscopy_files_tobe_delivered_array[0], permissions, protocol, newPermissionString);
                    }

                    //Renaming back the file to Original FileName
                    boolean osCopyFileRenamed = osCopyService.renameOsCopyTempFile(remoteDirectory + oscopy_files_tobe_delivered_array[0], remoteDirectory + oscopy_files_tobe_delivered_array[2]);
                    buff_buff.write(":: => " + LocalDateTime.now() + " : Thread.Name :-  " + threadName + " :- OSCOPY Renaming from:-  " + oscopy_files_tobe_delivered_array[0] + " to " + oscopy_files_tobe_delivered_array[2] + osCopyFileRenamed);
                    buff_buff.newLine();

                } else {
                    try {
                        osCopyService.copyToOS(oscopy_files_tobe_delivered_array[6], remoteDirectory + oscopy_files_tobe_delivered_array[2]);
                        if (!permissions.equals("") && changePermissionCheck) {
                            consumerProtocolChangePermissions(null, null, changeDirector + oscopy_files_tobe_delivered_array[2], permissions, protocol, newPermissionString);
                        }

                    } catch (IOException e) {
                        if (oscopy_tmp_error_count == 0) {
                            if (Math.round((double) (oscopy_error_count + 1) / 1 * 100) >= deliveryDetails.getDeliveryDetailsEntity().getDeliveryFailureRate()) { //Still not reached failure limit so continue to fail files
                                oscopy_error_count++;
                                if (doesSFGFilExist(localDirectory + oscopy_files_tobe_delivered_array[0])) {
                                    deleteTempDirs(localDirectory, oscopy_files_tobe_delivered_array[0]);
                                }
                            }
                        }
                    }
                }

                if (!notificationFileExtension.isEmpty()) {
                    String tempFileName[] = oscopy_files_tobe_delivered_array[2].split("\\.");
                    existingExtension = tempFileName[tempFileName.length - 1];
                    notificationFileName = oscopy_files_tobe_delivered_array[2].replace(existingExtension, notificationFileExtension);

                    try {
                        osCopyService.copyToOS(notificationSourceFile, remoteDirectory + notificationFileName);
                        if (!permissions.equals("") && changePermissionCheck) {
                            consumerProtocolChangePermissions(null, null, remoteDirectory + notificationFileName, permissions, protocol, newPermissionString);
                        }

                    } catch (IOException not) {
                        os_copy_delivery_status = "92";
                        if (oscopy_tmp_error_count == 0) {
                            if (Math.round((double) (oscopy_error_count + 1) / 1 * 100) >= deliveryDetails.getDeliveryDetailsEntity().getDeliveryFailureRate()) { //Still not reached failure limit so continue to fail files
                                oscopy_error_count++;
                                if (doesSFGFilExist(localDirectory + oscopy_files_tobe_delivered_array[0])) {
                                    deleteTempDirs(localDirectory, oscopy_files_tobe_delivered_array[0]);
                                }
                            }
                        }
                    }
                }

                oscopy_route_metaData = oscopy_files_tobe_delivered_array[4].split(",");
                if (deliveryDetails.getDeliveryDetailsEntity().getAgeAnalysis().equalsIgnoreCase("Yes") && os_copy_delivery_status.equalsIgnoreCase("33")) {
                    oscopy_age_analysis_list.add("y;" + Integer.parseInt(oscopy_files_tobe_delivered_array[0]) + ";" + oscopy_files_tobe_delivered_array[6] + ";" + oscopy_files_tobe_delivered_array[2] + ";" + oscopy_files_tobe_delivered_array[2] + ";"
                            + oscopy_route_metaData[0] + ";" + oscopy_route_metaData[2] + ";" + oscopy_files_tobe_delivered_array[5] + ";" + oscopy_files_tobe_delivered_array[3]
                            + "," + oscopy_files_tobe_delivered_array[4] + ";n;1;0;" + new Date().getTime() + ";33;FTP");

            }
        }

        Date delivery_date = new Date(System.currentTimeMillis());
        if (os_copy_delivery_status.equalsIgnoreCase("33")) {
            oscopy_proc_files_count++;
            deletePendingDeliveriesFilesByPuid(oscopy_files_tobe_delivered_array[0]);
            updateSuccessDeliveredFileTsDeliverStatusAndKeyByDeliverUid(delivery_date, os_copy_delivery_status, oscopy_route_metaData[2], Integer.parseInt(oscopy_files_tobe_delivered_array[5]));
        }
        else {
            deletePendingDeliveriesFilesByPuid(oscopy_files_tobe_delivered_array[0]);
            delivery_date = null;
            updateFailedDeliveredFileTsStatusAndKeyByUid(oscopy_route_metaData[2], Integer.parseInt(oscopy_files_tobe_delivered_array[5]));
        }

        if (deliveryDetails.getDeliveryDetailsEntity().getUseCompress().equalsIgnoreCase("yes") && doesSFGFilExist(localDirectory + oscopy_files_tobe_delivered_array[0])){
            directory_service.deleteNonEmptyDirectoryUsingStream(localDirectory + oscopy_files_tobe_delivered_array[0]);
        }


        if(Math.round((double)oscopy_error_count / 1 * 100) >= deliveryDetails.getDeliveryDetailsEntity().getDeliveryFailureRate()){
            autoSuspendDelivery(deliveryDetails.getConsumerCode(), deliveryDetails.getRouteShortName());
        }

        if (oscopy_age_analysis_list.size() > 0) {
            for (String age_analysis_listToProcess : oscopy_age_analysis_list) {
                ageAnalysisService.ageAnalyseFileProcessing(age_analysis_listToProcess, deliveryDetails.getRouteShortName());
            }
        }
        return oscopy_proc_files_count;
    }


    private Map<String, String> getFileProcessingOptions(DeliveryDetailsEntity deliveryDetails, String threadName, BufferedWriter bw)throws IOException {

        Map<String, String> deliver_process_option = new HashMap<>();
        /*List<String> deliver_process_option = new ArrayList();
        deliver_process_option.add("");
        deliver_process_option.add("");
        deliver_process_option.add("");
        deliver_process_option.add("");*/

        //Compress files
        bw.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :- About to call Delivery Process Options. Option is :- "+ deliveryDetails.getUseCompress());
        bw.newLine();

        if (getUseCompress(deliveryDetails.getUseCompress()).equalsIgnoreCase("yes")) { //gzip, zip, tar supported for now
            String compression_position = getCompressPosition(deliveryDetails.getCompressPosition());
            //deliver_process_option.set(Integer.parseInt(compression_position) - 1, "compress");
            deliver_process_option.put("compress", "compress");
        }
        //Uncompress files
        if (getUseUncompress(deliveryDetails.getUseUncompress()).equalsIgnoreCase("yes")) { //gzip, zip, tar supported for now
            bw.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :- UnCompress Delivery Process Options Invoked...");
            bw.newLine();
            String uncompress_pos = getUncompressPosition(deliveryDetails.getUncompressPosition());

            //eliver_process_option.set(Integer.parseInt(uncompress_pos) - 1, "uncompress");
            deliver_process_option.put("uncompress", "uncompress");
        }
        //Rename files
        if (getChangeFileNameCase(deliveryDetails.getChangeFilenameCase()).equalsIgnoreCase("yes")) {
            bw.write(LocalDateTime.now() +  ": Thread.Name :-  " + threadName + " :- Change File Name Delivery Process Options Invoked...");
            bw.newLine();
            String rename_pos = getRenamePosition(deliveryDetails.getRenamePosition());
            //deliver_process_option.set(Integer.parseInt(rename_pos) - 1, "rename");
            deliver_process_option.put("rename", "rename");
        }
        //Remove characters
        if (getRemoveChars(deliveryDetails.getRemoveChars()).equalsIgnoreCase("yes")) {
            bw.write(LocalDateTime.now() + ": Thread.Name :-  " + threadName + " :- Remove Chars Delivery Process Options Invoked...");
            bw.newLine();
            String remove_chars = getRemoveCharsPosition(deliveryDetails.getRemovePosition());
            //deliver_process_option.set(Integer.parseInt(remove_chars) - 1, "remove");
            deliver_process_option.put("remove", "remove");
        }
        return deliver_process_option;
    }

    private String getCompressPosition(String compressPosition) {
        if (compressPosition == null || compressPosition.equalsIgnoreCase("Null")) {
            compressPosition = "";
        }
        return compressPosition;
    }

    public String getUncompressType(String uncompressType) {
        if (uncompressType == null) {
            uncompressType = "";
        }
        return uncompressType;
    }

    private String getUncompressPosition(String uncompressPosition) {
        if (uncompressPosition == null || uncompressPosition.equalsIgnoreCase("Null")) {
            uncompressPosition = "";
        }
        return uncompressPosition;
    }

    private String getRemoveCharsPosition(String removeCharsPosition) {
        if (isEmpty(removeCharsPosition)) {
            removeCharsPosition = "";
        }
        return removeCharsPosition;
    }

    private String getRenamePosition(String renamePosition) {
        if (isEmpty(renamePosition) || renamePosition.equalsIgnoreCase("Null")) {
            renamePosition = "";
        }
        return renamePosition;
    }

    private String getNotificationFileExt(String notificationFileExt) {
        if (isEmpty(notificationFileExt)) {
            notificationFileExt = "";
        }
        return notificationFileExt.trim();
    }

    private String getChmodDetails(String chmod_parms) {

        if (isEmpty(chmod_parms) || chmod_parms.toLowerCase().contains("ull")) {
            chmod_parms = "";
        } else if (chmod_parms.length() < 4) {
            for (int i = 0; i < chmod_parms.length(); i++) {
                chmod_parms = "0" + chmod_parms;
                if (chmod_parms.length() > 3) {
                    break;
                }
            }
        }
        return chmod_parms.trim();
    }

    private String getChangePermissionsDetails(String change_perm_value) {
        if (change_perm_value.contains("y") || change_perm_value.contains("Y")) {
            change_perm_value = "yes";
        } else {
            change_perm_value = "no";
        }
        return change_perm_value.trim();
    }

    private String getRemoteAction(String dest_file_action) {

        if (isEmpty(dest_file_action) || dest_file_action.toLowerCase().contains("ull")) {
            dest_file_action = "NONE";

            if (dest_file_action.trim().equalsIgnoreCase("DELETE")) {
                dest_file_action = "REMOVE";
            }
            dest_file_action.trim().toUpperCase();
        }
        return dest_file_action;
    }

    private String getDeliverWithTempName(String use_temp_name) {
        if (isEmpty(use_temp_name) || use_temp_name.toLowerCase().contains("n") || use_temp_name.toLowerCase().contains("ull")) {
            use_temp_name = "no";
        }
        if (use_temp_name.contains("y") || use_temp_name.contains("Y")) {
            use_temp_name = "yes";
        }
        return use_temp_name.trim();
    }

    private String renameAFile(String file_values, String filePattern) {
        file_values = renameService.renameByRegEx(file_values, mftValidationService.getServerFileRenameCases(filePattern), filePattern);

        return file_values;
    }

    private void removeCharsFromAFile(String[] fileValues, String localDirectory, String removeChars) { //fileValues contain PD_UID;FILENAME_ON_DISK;DESTINATION_FILENAME;DATA_FLOWID;ROUTE_METADATA;DELIVER_UID;COPY_OF_FILENAME_ON_FISK to be replaced with zipped/stripped file
        File tempFileToRemoveDir = new File(localDirectory + "removeChar"); //create unique directory for each file to be manipulated
        tempFileToRemoveDir.mkdirs();
        BufferedReader br = null;
        BufferedWriter bwRemoveChars = null;
        String line = null;
        try {
            br = new BufferedReader(new FileReader(fileValues[1].trim()));
            bwRemoveChars = new BufferedWriter(new FileWriter(localDirectory.trim() + "removeChar/" + fileValues[2].trim(), true));
            while ((line = br.readLine()) != null) {
                line = line.replaceAll("[\r\n]", "");
                switch (removeChars) {
                    case "CR":
                        line = line + "\n";
                        break;
                    case "LF":
                        line = line + "\r";
                        break;
                    case "ADD":
                        line = line + "\r\n";
                    default:
                        break;
                }
                bwRemoveChars.write(line);
            }
            br.close();
            bwRemoveChars.close();
            fileValues[6] = localDirectory.trim() + "removeChar/" + fileValues[2].trim();
        } catch (Exception brEx) {
            brEx.printStackTrace();
        }
    }

    private String getDateFormat() {
        Date dateNow = new Date();
        Format dtFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String date_string_to_file = dtFormat.format(dateNow);
        return date_string_to_file;
    }

    private void consumerProtocolChangePermissions(FTPClient ftpClient, ChannelSftp sftpClient, String path_to_file_name, String chmod, String protocol, String newPermissionString) throws IOException, SftpException {

        int octInt = Integer.parseInt(getChmodDetails(newPermissionString).trim().substring(getChmodDetails(newPermissionString).trim().length() - 3, getChmodDetails(newPermissionString).trim().length()), 8);

        switch (protocol) {
            case "FTP":
                ftpClient.sendSiteCommand("chmod " + chmod + " " + path_to_file_name);
                break;
            case "SFTP":
                sftpClient.chmod(octInt, path_to_file_name);
                break;
            case "OSCOPY":
                Runtime.getRuntime().exec("chmod " + chmod + " " + path_to_file_name);
                break;
            default:
                log.error(":: ===> No Protocols specified for consumerProtocolChangePermissions().. " + "\n ConsumerProtocol :=> " + protocol);
        }
    }

    private static boolean isEmpty(String value_to_validate) {
        return value_to_validate == null || value_to_validate.trim().equalsIgnoreCase("");
    }

    private String  getUseCompress(String useCompress) {
        if (isEmpty(useCompress) || (!useCompress.equalsIgnoreCase("yes") && !useCompress.equalsIgnoreCase("no"))) {
            useCompress = "no";
        }
        return useCompress;
    }

    private String getUseUncompress(String useUncompress) {
        if (isEmpty(useUncompress) || (!useUncompress.equalsIgnoreCase("yes") && !useUncompress.equalsIgnoreCase("no"))) {
            useUncompress = "no";
        }
        return useUncompress;
    }

    private String getChangeFileNameCase(String changeFileNameCase) {
        if (isEmpty(changeFileNameCase) || (!changeFileNameCase.equalsIgnoreCase("yes") && !changeFileNameCase.equalsIgnoreCase("no"))) {
            changeFileNameCase = "no";
        }
        return changeFileNameCase;
    }

    private String getRemoveChars(String removeChars) {
        if (isEmpty(removeChars) || (!removeChars.equalsIgnoreCase("yes") && !removeChars.equalsIgnoreCase("no"))) {
            removeChars = "no";
        }

        return removeChars;
    }

    private String getRemoteDirectory(String remoteDirectory, String protocol, String ftpHostName, String remoteHostName) {
        if (isEmpty(remoteDirectory) || remoteDirectory.toLowerCase().contains("ull")) {
            remoteDirectory = "";
        }
        if (!remoteDirectory.endsWith("/")) {
            remoteDirectory = remoteDirectory + "/";
        }
        remoteDirectory = replaceDynamicVariables(remoteDirectory.trim(), protocol, ftpHostName, remoteHostName);

        return remoteDirectory;
    }

    private String replaceDynamicVariables(String dir, String protocol, String ftpHostName, String remoteHostName) {
        String partString = "", dateString = "";
        int pos = 0, startFormat = 0, endFormat = 0;
        String token = "", dateFormat = "";
        Format dtFormat = null;

        if (dir.contains("$CurrDate") || dir.contains("$PrevDate")) {
            if (dir.contains("$CurrDate")) {
                pos = dir.indexOf("[$CurrDate-");
            } else {
                pos = dir.indexOf("[$PrevDate-");
            }
            partString = dir.substring(pos, dir.length());
            StringTokenizer st = new StringTokenizer(partString, "*");

            while (st.hasMoreTokens()) {
                token = st.nextToken();
                startFormat = token.indexOf("-");
                endFormat = token.indexOf("]");
                dateFormat = token.substring(startFormat + 1, endFormat).trim();
            }
            Date dateNow = new Date();
            dtFormat = new SimpleDateFormat(dateFormat);
            if (dir.contains("$PrevDate")) {
                long longDate = dateNow.getTime() - 86400000; // subtract 1 day in milliseconds
                dateNow.setTime(longDate);
            }
            dateString = dtFormat.format(dateNow);
            dir = dir.replace(dir.substring(pos, (pos + endFormat + 1)), dateString);
        }


        while (dir.contains("$CurrDate") || dir.contains("$PrevDate")) {
            if ("FTP".equalsIgnoreCase(protocol)) {
                dir = dir.replace("[$Hostname]", ftpHostName);
            } else {
                dir = dir.replace("[$Hostname]", remoteHostName);
            }
        }
        return dir;
    }

    private String getFTPSSL(String ftpSSL) {
        if (isEmpty(ftpSSL) || ftpSSL.toLowerCase().contains("ull") || ftpSSL.equalsIgnoreCase("NONE")) {
            ftpSSL = "SSL_NONE";
        }
        return ftpSSL.trim();
    }

    private String getQuoteCommand(String quoteCommand) {
        if (isEmpty(quoteCommand) || quoteCommand.toLowerCase().contains("ull")) {
            quoteCommand = "";
        }
        return quoteCommand.trim();
    }

    private static boolean sftpFileExists(ChannelSftp channelSftp, String path) {
        Vector check_results = null;
        try {
            check_results = channelSftp.ls(path);
        } catch (SftpException e) {
            if (e.id == channelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            log.error(new Date().toString() + " Unexpected exception during ls files on sftp: [{}:{}]", e.id, e.getMessage());
        }
        return check_results != null && !check_results.isEmpty();
    }

    private void deleteTempDirs(String localDirectory, String fileName) throws IOException {
        System.out.println("+++++++++++++localDirectory = " + localDirectory +" : Filename" +  fileName);

        Path delete_directory = Paths.get(localDirectory + fileName);
        System.out.println("+++++++++++++DELETE Directory = " + localDirectory +  fileName);
        Files.delete(delete_directory);
        log.info(new Date().toString() + ": Deleted Temp Directory  : - " + localDirectory + fileName + ".....");
    }

    private boolean autoSuspendDelivery(String consumerCode, String routeShortName){
        log.info(new Date().toString() + ": Auto Suspending Delivery : "+ consumerCode  + "  : Route Short Name :-  " + routeShortName );
        if (updateStatusForDeliverySuspension(consumerCode, routeShortName) > 0 ){
            return true;
        }else {
            log.info(new Date().toString() + ": Auto Suspending Delivery For : "+ consumerCode  + "  : Route Short Name :-  " + routeShortName + "  FAILED !!!!" );
            return false;
        }
    }
    private void updateSuccessFulDeliveredFiles(String file_pd_uid ){

        deletePendingDeliveriesFilesByPuid(file_pd_uid);
    }

    private void deletePendingDeliveriesFilesByPuid(String pd_uid) {
        pendingDeliveriesRepository.deleteByPdUid(pd_uid);
        boolean isFound = pendingDeliveriesRepository.existsByPdUid(pd_uid);
        if (!isFound){
            log.info(new Date().toString() + ":  File Record of PD_UID :- "+ pd_uid+ "  has been deleted");
        }else {
            log.warn(new Date().toString() + ":  Problems Deleting File Record of PD_UID :- "+pd_uid+ " Please Investigate..");
        }
    }
    private void deletePendingDeliveriesFilesByConsumerCodeAndRouteShortName(String consumer_code, String route_short_name) {

        pendingDeliveriesRepository.deleteAllByConsumerCodeAndRouteShortName(consumer_code, route_short_name);
        boolean isFound = pendingDeliveriesRepository.existsByConsumerCodeAndRouteShortName(consumer_code, route_short_name);
        if (!isFound){
            log.info(new Date().toString() + ":  Consumer Record :- "+ consumer_code+ " with Route Short Name: " + route_short_name+ " has been deleted");
        }else {
            log.warn(new Date().toString() + ":  Problems Deleting Consumer Record:- "+consumer_code+ "with Route Short Name: " + route_short_name+ " Please Investigate..");
        }
    }

    private int updateStatusForDeliverySuspension(String consumer_code, String route_short_name){

        return delivery_details_repo.updateStatusByConsumerCodeAndRouteShortName(consumer_code, route_short_name);
    }

    private void updateFailedDeliveredFileTsStatusAndKeyByUid(String del_file_key, int del_file_uid) {
        delivered_file_entity_repo.updateDeliveredFileTsStatusAndKeyByUid(del_file_key, del_file_uid);
    }
    private void updateSuccessDeliveredFileTsDeliverStatusAndKeyByDeliverUid(Date consumer_deliveryts, String delivery_status, String delivery_key, int delivery_uid){
        delivered_file_entity_repo.updateDeliveredFileTsDeliverStatusAndKeyByDeliverUid(consumer_deliveryts, delivery_status, delivery_key, delivery_uid);
    }
    private String getRPADSqlStringValue(String string_value){
        String value_of_sub = null;
        log.info(new Date().toString() + ": RPAD SQL Value Before: " + string_value + " of Length :- : " + string_value.length() );

        if  (string_value != null) {
            if (string_value.length() > 24) {
                value_of_sub = string_value.substring(0,24);
            } else {
                value_of_sub = string_value;
                int count = 24 - string_value.length();
                log.info(new Date().toString() + ": COUNT VALUE FOR XTERS TO ADDED  :- " + count);
                for (int i =1; i<=count; i++ ) {
                    value_of_sub = value_of_sub + " ";
                }
            }
        }
        log.info(new Date().toString() + ": Value : " + value_of_sub + ":of Length :- : " + value_of_sub.length());
        return value_of_sub;
    }
    private long calculateFailureRateValue(int error_count, int total_file_count){

        long failure_rate_value = Math.round((double)(error_count+1) / total_file_count * 100);

        return failure_rate_value;
    }


    private String uncompressAFile(String localDirectory, String file_value_0, String file_name_on_disc_1, String file_name_2, String file_name_6, String uncompress_type, String thread_name, BufferedWriter bw_cmp) { //fileValues contain PD_UID;FILENAME_ON_DISK;DESTINATION_FILENAME;DATA_FLOWID;ROUTE_METADATA;DELIVER_UID;COPY_OF_FILENAME_ON_FISK to be replaced with zipped/stripped file
        String final_uncompressed_file = "";
        try{
            bw_cmp.write(LocalDateTime.now() + " : Thread.Name :-  " + thread_name + ": Filename before uncompression is : " + file_name_2);
            bw_cmp.newLine();
            Path tempFile_to_uncompress_dir = Paths.get(localDirectory + file_value_0); //create unique directory for each file to be uncompressed
            Files.createDirectory(tempFile_to_uncompress_dir);
            String uncompressed_directory = localDirectory + file_value_0 + "/";
            String uncompressed_file_name = "";

            uncompressed_file_name = compressService.decompressFile(file_name_on_disc_1,
                    file_name_2,
                    uncompressed_directory,
                    uncompress_type,
                    thread_name,
                    bw_cmp);
            if (!uncompressed_file_name.equals("")){
                file_name_2 = uncompressed_file_name;
                file_name_6 = uncompressed_directory + uncompressed_file_name;
                final_uncompressed_file = file_name_2+";"+file_name_6;

                bw_cmp.write(new Date().toString() + " : Thread.Name :-  " + thread_name + ":  New name after uncompression is : " + file_name_2);
                bw_cmp.newLine();
            }else {
                bw_cmp.write(new Date().toString() + " : Thread.Name :-  " + thread_name + ": Could not uncompress file. Please investigate. File : " + file_name_2);
                bw_cmp.newLine();
            }
        }catch(IOException ex){
            log.error(new Date().toString() + " : Error occured while trying to uncompress file. "+ ex.getLocalizedMessage());
        }
        return final_uncompressed_file;
    }
     private boolean doesSFGFilExist(String sfg_fs_file){

         log.info("About to check if SFG File exists in the FileSystem : - > " + sfg_fs_file);
        boolean sfg_file_fs =  new File(sfg_fs_file).exists();

        return sfg_file_fs;
     }

}
