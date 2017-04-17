package com.doordeck.common.disk;

import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

public class DiskCleaner {

    private final Logger LOG = LoggerFactory.getLogger(DiskCleaner.class);

    // FIXME better exception handling
    public static void main(String args[]) throws Exception {
        new DiskCleaner();
    }

    private volatile boolean interrupted = false;

    public DiskCleaner() throws Exception {
        /*
         * 1) Identify disk
         * 2) Unmount disk
         * 3) Boot VM
         * 4) Clean up VM
         * 5) Eject disk
         */
        LOG.info("Looking for SD card... (press CTRL+C to cancel)");

        String device = null;

        while (!interrupted && device == null) {
            device = MountUtils.getRaspberryPiBootDisk().orElse(null);
        }

        // Unmounting disk
        LOG.info("Found {}, unmounting...", device);
        unmountDisk(device);

        // Taking ownership of disk
        String whoami = System.getProperty("user.name");
        checkState(whoami != null, "Unable to determine current user");

        LOG.info("Taking temporary ownership of SD card");
        String[] chown = {
                "osascript",
                "-e",
                "do shell script \"chown " + whoami + " " + device + "\" with administrator privileges" };
        ProcessResult ownDeviceResult = new ProcessExecutor(chown)
                .redirectOutput(Slf4jStream.ofCaller().asInfo())
                .execute();
        checkState(ownDeviceResult.getExitValue() == 0, "Unable to own device, got exit code " + ownDeviceResult.getExitValue());

        // Creating link for VirtualBox
        LOG.info("Creating VMDK");
        String sdcardVmdk = "sd-card.vmdk";
        new File(sdcardVmdk).delete(); // Delete file if exists
        String[] vboxManage = {
                "VBoxManage",
                "internalcommands",
                "createrawvmdk",
                "-filename",
                sdcardVmdk,
                "-rawdisk",
                device };
        ProcessResult vboxManageResult = new ProcessExecutor(vboxManage)
                .redirectOutput(Slf4jStream.ofCaller().asInfo())
                .execute();
        checkState(vboxManageResult.getExitValue() == 0, "Unable to own create VMDK device, got exit code " + ownDeviceResult.getExitValue());

        // Unmount disk (again)
        LOG.info("Unmounting disk (again)");
        unmountDisk(device);

        // Start Vagrant
        LOG.info("Starting Vagrant");
        ProcessResult vagrantResult = new ProcessExecutor().command("vagrant", "up", "--destroy-on-error")
                .redirectOutput(Slf4jStream.ofCaller().asInfo())
                .execute();
        checkState(vagrantResult.getExitValue() == 0, "Unable to start vagrant, got exit code " + vagrantResult.getExitValue());

        // Destroy vagrant box
        LOG.info("Cleaning up vagrant box");
        new ProcessExecutor().command("vagrant", "destroy", "-f")
                .redirectOutput(Slf4jStream.ofCaller().asInfo())
                .execute();
        new File(sdcardVmdk).delete(); // Delete file if exists

        LOG.info("Ejecting SD card...");
        ProcessResult ejectResult = new ProcessExecutor().command("diskutil", "eject", device)
                .redirectOutput(Slf4jStream.ofCaller().asInfo()).execute();
        checkState(ejectResult.getExitValue() == 0, "Ejecting disk failed, got exit code " + ejectResult.getExitValue());
    }

    private void unmountDisk(String device) throws Exception {
        ProcessResult unmountResult = new ProcessExecutor().command("diskutil", "unmountDisk", device)
                .redirectOutput(Slf4jStream.ofCaller().asInfo()).execute();
        checkState(unmountResult.getExitValue() == 0, "Unmounting disk failed, got exit code " + unmountResult.getExitValue());
    }

}
