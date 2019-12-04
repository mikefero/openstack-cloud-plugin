package jenkins.plugins.openstack.compute;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * This class as designed, is not supposed to be shared among multiple computers.
 * Implementation of Serializable for Pipeline purposes
 */
public class JCloudsLauncher extends DelegatingComputerLauncher implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(JCloudsLauncher.class.getName());

    public JCloudsLauncher(@Nonnull ComputerLauncher launcher) {
        super(launcher);
    }

    @Override
    public void launch(@Nonnull SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        Slave n = computer.getNode();
        if (!(n instanceof JCloudsSlave)) {
            LOGGER.warning(getClass().getSimpleName() + " used to launch incompatible computer type " + computer.getClass());
            return;
        }

        JCloudsSlave node = (JCloudsSlave) n;
        Integer configuredTimeout = node.getSlaveOptions().getStartTimeout();
        if (configuredTimeout == null) throw new NullPointerException();
        long timeout = node.getCreatedTime() + configuredTimeout;
        do {
            launcher(computer).launch(computer, listener);
            if (computer.getChannel() != null) return;

            listener.getLogger().println("Launcher failed to bring the node online. Retrying ...");

            Thread.sleep(2000);
        } while (System.currentTimeMillis() < timeout);

        listener.getLogger().println("Launcher failed to bring the node online within timeout.");
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        try {
            launcher(computer).afterDisconnect(computer, listener);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create launcher", e);
        }
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        try {
            launcher(computer).beforeDisconnect(computer, listener);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create launcher", e);
        }
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }

    @SuppressFBWarnings({
            "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            "Since 2.1, tha launcher is injected in constructor but we need this as a fallback for the slaves that survived the upgrade."
    })
    private ComputerLauncher launcher(SlaveComputer computer) throws IOException {
        if (launcher != null) return launcher;

        final JCloudsSlave slave = (JCloudsSlave) computer.getNode();

        return launcher = slave == null
                ? new JNLPLauncher(false) // Return something harmless to prevent NPE
                : slave.getLauncherFactory().createLauncher(slave)
        ;
    }
}
