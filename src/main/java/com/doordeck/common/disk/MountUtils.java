package com.doordeck.common.disk;

import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class MountUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MountUtils.class);
    private static final String PI_BOOT_LABEL = "PI_BOOT";
    private static final String PI_BOOT_TYPE = "msdos";
    private static final String DISK_PREFIX = "/dev/disk";
    private static final Splitter SPACE_SPLITTER = Splitter.on(' ').limit(2);
    private static final Splitter PARTITION_SPLITTER = Splitter.on('s').limit(2);

    public static void main(String[] args) throws Exception {
        LOG.info("{}",MountUtils.getRaspberryPiBootDisk().orElseThrow(IllegalStateException::new));
    }

    public static Optional<String> getRaspberryPiBootDisk() {
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            String mountPoint = SPACE_SPLITTER.split(store.toString()).iterator().next();
            boolean isRaspberryPiBootDisk = mountPoint.endsWith(PI_BOOT_LABEL) && PI_BOOT_TYPE.equals(store.type());
            LOG.trace("Checking {} (type: {}, path: {})... {}", store.name(), store.type(), mountPoint, isRaspberryPiBootDisk);

            if (isRaspberryPiBootDisk) {
                return Optional.of(getRootDevice(store.name()));
            }
        }
        return Optional.empty();
    }

    private static String getRootDevice(String device) {
        // Strips off partition information to get root device
        // expects device format to be /dev/disk4s1

        checkArgument(device.startsWith(DISK_PREFIX), "Expected device to started with " + DISK_PREFIX + " but got " + device);

        String deviceIdAndPartition = device.substring(DISK_PREFIX.length(), device.length());
        String deviceId = PARTITION_SPLITTER.split(deviceIdAndPartition).iterator().next();

        return device.substring(0, DISK_PREFIX.length() + deviceId.length());
    }

}
