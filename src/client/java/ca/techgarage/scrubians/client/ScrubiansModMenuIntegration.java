package ca.techgarage.scrubians.client;

import ca.techgarage.bscm.client.BSCMModMenuIntegration;
import ca.techgarage.scrubians.ScrubiansConfig;

public class ScrubiansModMenuIntegration extends BSCMModMenuIntegration {
    public ScrubiansModMenuIntegration() {
        super(ScrubiansConfig.class, "scrubians");
    }
}
