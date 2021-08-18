package za.co.vodacom.vodacommft.config;
/**
 * @author ncubeh on 2020/05/05
 * @package za.co.vodacom.vodacomMFT.config
 */

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Date;

@Getter
@Setter
/*@Component
@ConfigurationProperties
@PropertySource(value = {
        "file:${app.SI_INSTALL}/properties/VC_collection.properties",
        "file:${app.SI_INSTALL}/properties/vc_delivery.properties",
        "file:${app.SFG_HOME}/si_globals/si_global.properties",
        "file:${app.SI_INSTALL}/properties/vc.properties",
        "file:${app.SI_INSTALL}/properties/vc_ageanalysis.properties"},
        ignoreResourceNotFound = true)*/
@Component
@Configuration
@PropertySource(value = {"file:${app.SI_INSTALL}/VC_collection.properties",
        "file:${app.SI_INSTALL}/vc_delivery.properties",
        "file:${app.SFG_HOME}/si_global.properties",
        "file:${app.SI_INSTALL}/vc.properties",
        "file:${app.SI_INSTALL}/vc_ageanalysis.properties"})
public class PropertiesFileSysConfig {

    @Autowired
    private Environment environment;


    @Value("${PUBLIC_KEY_FILE:}")
    private String publicKey;

    @Value("${LOCAL_WORKING_DIR:}")
    private String localWorkingDirectory;

    @Value("${COL_LOCAL_WORKING_DIR:}")
    private String collLocalWorkingDirectory;

    @Value("${CLEANUP_CUTOFF:}")
    private String cleanupCutOff;

    @Value("${COLLECTION_ERRORS_LIST:}")
    private String collectionErrorsList;

    @Value("${SITE_COMMAND:}")
    private String siteCommand;

    @Value("${SALT:}")
    private String salt;

    @Value("${FILE_COLLECTED:}")
    private String fileCollectedStatus;

    @Value("${CHECK_PROC_SCRIPT:}")
    private String checkProcessesScript;

    @Value("${SFG_CONNECTION_TYPE:}")
    private String sfgConnectionType;

    @Value("${SFG_HOST:}")
    private String sfgHost;

    @Value("${SFG_FTP_PORT:0}")
    private int sfgPort;

    @Value("${SFG_USER:}")
    private String sfgUser;

    @Value("${SFG_PASSWORD:}")
    private String sfgPassword;

    @Value("${DELIVERY_BATCH_SIZE:0}")
    private int deliveryBatchSize;

    @Value("${BUCKET1:}")
    private String bucket1;

    @Value("${BUCKET2:}")
    private String bucket2;

    @Value("${BUCKET3:}")
    private String bucket3;

    @Value("${BUCKET4:}")
    private String bucket4;

    @Value("${BUCKET5:}")
    private String bucket5;

    @Value("${BUCKET6:}")
    private String bucket6;

    @Value("${BUCKET7:}")
    private String bucket7;

    @Value("${BUCKET8:}")
    private String bucket8;

    @Value("${BUCKET9:}")
    private String bucket9;

    @Value("${ERROR_REPLAY_DEL:}")
    private String errorReplayDelivery;

    @Value("${REPLAYED_DEL:}")
    private String replayedDelivery;

    @Value("${NOTIFICATION_SOURCE_FILE:}")
    private String notificationSourceFile;

    @Value("${LOCK_TIMEOUT_MS:}")
    private String lockTimeOut;

    @Value("${SCHEDULED_FIXED_DELAY:}")
    private String scheduledInitialDelay;

    @Value("${SCHEDULED_FIXED_DELAY:}")
    private String scheduledFixedDelay;

    @Value("${DELIVERY_SCHEDULE_SWITCHED:}")
    private String switchDeliveryScheduleOnAndOff;

    @Value("${THREAD_TUNING:}")
    private String threadTuningProperty;

    @Value("${THREAD_FILE_RANGE_100:0}")
    private int threadTuneValueFor100rLess;

    @Value("${THREAD_FILE_RANGE_200:0}")
    private int threadTuneValueFor200rLess;

    @Value("${THREAD_FILE_RANGE_1000:0}")
    private int threadTuneValueFor100OrLess;

    @Value("${THREAD_FILE_RANGE_5000:0}")
    private int threadTuneValueFor500OrLess;

    @Value("${THREAD_FILE_RANGE_5000_PLUS:0}")
    private int threadTuneValueFor500OrMore;

    @Value("${THREAD_CORE_SIZE:0}")
    private int corePoolSize;

    @Value("${THREAD_MAX_POOL_SIZE:0}")
    private int maxPoolSize;

    @Value("${THREAD_QUEUE_CAPACITY:0}")
    private int queueCapacity;

    @Value("${THREAD_CONCURRENCY_LIMIT:0}")
    private int concurrencyLimit;

    @Value("${CONCURRENT_NUMBER_OF_THREADS:0}")
    private int concurrentNumberOfThreadPool;

    @Value("${COL_THREAD_CORE_SIZE:0}")
    private int colCorePoolSize;

    @Value("${COL_THREAD_MAX_POOL_SIZE:0}")
    private int colMaxPoolSize;

    @Value("${COL_THREAD_QUEUE_CAPACITY:0}")
    private int colqueueCapacity;

    @Value("${COL_THREAD_CONCURRENCY_LIMIT:0}")
    private int colConcurrencyLimit;

    @Value("${COL_CONCURRENT_NUMBER_OF_THREADS:0}")
    private int colConcurrentNumberOfThreadPool;


    public String getSftpPassword(String remote_user){
        String sftp_password = environment.getProperty(remote_user+".PASSWORD");
        return sftp_password;
    }

}
