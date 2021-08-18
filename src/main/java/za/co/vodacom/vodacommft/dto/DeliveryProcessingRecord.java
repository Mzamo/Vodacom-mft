package za.co.vodacom.vodacommft.dto;

import lombok.*;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class DeliveryProcessingRecord {

    private String consumerCode;
    private String routeShortName;
    private String localDirectory;
    private String workDirectory;
}
