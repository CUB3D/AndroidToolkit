import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarUtils;
import org.apache.commons.compress.utils.IOUtils;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by Callum on 13/08/2015.
 */
public class GUIMain
{
    private JProgressBar progressBar1;
    private JTabbedPane tabbedPane1;
    private JTextField textField1;
    private JButton button1;
    private JButton bootFromRecoveryButton;
    private JButton flashRecoveryButton;
    private JPanel mainPanel;
    private JLabel currentAction;
    private JTextPane statusLog;
    private JButton button2;
    private JTextField textFieldfactoryZip;
    private JButton flashFactoryButton;
    private JTextPane textPane1;
    private JCheckBox flashAllCheckBox;


    public static JFrame frame;
    public static GUIMain instance;

    public static void main(String[] args)
    {
        instance = new GUIMain();

        frame = new JFrame("GUIMain");
        frame.setContentPane(instance.mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        instance.bootFromRecoveryButton.addActionListener((al) -> bootFromRecovery());
        instance.flashRecoveryButton.addActionListener((al) -> flashRecovery());
        instance.flashFactoryButton.addActionListener((al) -> flashFactory_());
    }

    private static void flashFactory_()
    {
        new Thread(() -> {
            flashFactory();
        }).start();
    }


    private static void flashFactory()
    {
        String image = instance.textFieldfactoryZip.getText();

        File in = new File(image);

        File out = new File(in.getName() + " - extract");

        try
        {
            out.delete();
        }catch(Exception e) {e.printStackTrace();}

        try
        {
            out.mkdirs();
        }catch(Exception e) {e.printStackTrace();}


        // extract tar from gzip

        File tarOut = new File(out, "ImageTar.tar");

        try
        {
            tarOut.createNewFile();
        }catch (Exception e) {e.printStackTrace();} //TODO: better error handling

        unGZIP(in, tarOut);

        // extract files from tar

        unTar(tarOut, out);

        File extractedFolder = null;

        for(File file : out.listFiles())
        {
            if(file.isDirectory())
            {
                // must be the extracted folder
                extractedFolder = file;
                break;
            }
        }

        if (extractedFolder == null)
        {
            //error, tar failed to extract
        }

        // remove all the stuff I don't need, such as the flash-all.bat/sh scripts
        // I only need the *.img files

        int len = extractedFolder.listFiles().length;
        setCurTask("Cleanup ", len, 0);
        int i = 0;
        for(File file : extractedFolder.listFiles())
        {
            i++;

            String name = file.getName();

            if(!name.endsWith(".zip") && !name.endsWith(".img"))
            {
                if(!file.delete())
                {
                    System.out.println("Couldn't delete unnecessary file, removing on exit");
                    file.deleteOnExit();
                }
            }
            setCurTask("Cleanup: " + file.getName(), len, i);
        }

        setCurTask("Cleanup ", len, len);

        // move everything from the extracted folder up a level, makes the flashing a bit easier

        len = extractedFolder.listFiles().length;
        setCurTask("Moving files ", len, 0);

        i = 0;
        for(File file : extractedFolder.listFiles())
        {
            i++;

            try
            {
                Files.move(file.toPath(), new File(out, file.getName()).toPath());
            } catch (IOException e) {e.printStackTrace();} //TODO: better error handling

            setCurTask("Moving " + file.getName(), len, i);
        }

        setCurTask("Moving files ", len, len);

        // get rid of all the other files I don't need, such as the tar archive

        len = out.listFiles().length;
        setCurTask("Cleanup ", len, 0);
        i = 0;

        for(File file : out.listFiles())
        {
            i++;

            String name = file.getName();

            if(!name.endsWith(".zip") && !name.endsWith(".img"))
            {
                if(!file.delete())
                {
                    System.out.println("Couldn't delete unnecessary file, removing on exit");
                    file.deleteOnExit();
                }
            }

            setCurTask("Cleanup " + file.getName(), len, 0);
        }

        setCurTask("Cleanup ", len, len);

        setCurTask("Finding zip", 1, 0);

        File imageZip = null;

        for(File f : out.listFiles())
        {
            if(f.getName().endsWith(".zip"))
            {
                imageZip = f;
            }
        }

        setCurTask("Finding zip", 1, 1);

        unzip(imageZip, out);

        // one final selective delete to get rid of unneeded files

        for(File file : out.listFiles())
        {
            String name = file.getName();

            if(!name.endsWith(".img"))
            {
                if(!file.delete())
                {
                    System.out.println("Couldn't delete unnecessary file, removing on exit");
                    file.deleteOnExit();
                }
            }
        }

        // find the bootloader

        setCurTask("Checking files", 6, 0);

        File bootloader = null;

        for(File file : out.listFiles())
        {
            if(file.getName().startsWith("bootloader-"))
            {
                bootloader = file;
                break;
            }

        }

        if(bootloader == null)
        {
            // all files must be present for a successful upgrade
            return;
        }

        setCurTask("Checking files", 6, 1);

        // find the radio

        File radio = null;

        for(File file : out.listFiles())
        {
            if(file.getName().startsWith("radio-"))
            {
                radio = file;
                break;
            }
        }

        if(radio == null)
        {
            // all files must be present for a successful upgrade
            return;
        }

        setCurTask("Checking files", 6, 2);

        // check for system

        File system = new File(out, "system.img");

        if(!system.exists())
        {
            return;
        }

        setCurTask("Checking files", 6, 3);

        // check for recovery

        File recovery = new File(out, "recovery.img");

        if(!recovery.exists())
        {
            return;
        }

        setCurTask("Checking files", 6, 4);

        // check for boot

        File boot = new File(out, "boot.img");

        if(!boot.exists())
        {
            return;
        }

        setCurTask("Checking files", 6, 5);

        File cache = new File(out, "cache.img");

        if(!cache.exists())
        {
            return;
        }

        setCurTask("Checking files", 6, 6);

        String msg = "Are you sure you want to flash this:\n";
        msg += "Bootloader: " + bootloader.getPath() + "\n";
        msg += "Radio: " + radio.getPath() + "\n";
        msg += "System: " + system.getPath() + "\n";
        msg += "Recovery: " + recovery.getPath() + "\n";
        msg += "Boot: " + boot.getPath() + "\n";
        msg += "Cache: " + cache.getPath() + "\n";

        if(JOptionPane.showConfirmDialog(GUIMain.instance.mainPanel, msg) == JOptionPane.OK_OPTION)
        {
            // now the fun part, flashing

            setCurTask("Flashing bootloader", 6, 0);

            CLIHelper.exec("adb reboot bootloader");
            CLIHelper.exec("fastboot flash bootloader \"" + bootloader.getPath() + "\"");

            setCurTask("Flashing radio", 6, 1);

            CLIHelper.exec("fastboot flash radio \"" + radio.getPath() + "\"");
            CLIHelper.exec("fastboot reboot-bootloader");
            setCurTask("Flashing system", 6, 2);
            CLIHelper.exec("fastboot flash system \"" + system.getPath() + "\"");
            setCurTask("Flashing recovery", 6, 3);
            CLIHelper.exec("fastboot flash recovery \"" + recovery.getPath() + "\"");
            setCurTask("Flashing boot", 6, 4);
            CLIHelper.exec("fastboot flash boot \"" + boot.getPath() + "\"");
            setCurTask("Flashing cache", 6, 5);
            CLIHelper.exec("fastboot flash cache \"" + cache.getPath() + "\"");
            setCurTask("Done: rebooting", 6, 6);
            CLIHelper.exec("fastboot reboot");
        }
    }

    private static void bootFromRecovery()
    {
        setCurTask("Rebooting into bootloader", 3, 1);
        CLIHelper.exec("adb reboot bootloader");
        setCurTask("Hot booting recovery", 3, 2);
        CLIHelper.exec("fastboot boot \"" + instance.textField1.getText() + "\"");
        setCurTask("Done!", 3, 3);

    }

    private static void flashRecovery()
    {
        setCurTask("Rebooting into bootloader", 3, 1);
        CLIHelper.exec("adb reboot bootloader");
        setCurTask("Flashing recovery", 3, 2);
        CLIHelper.exec("fastboot flash recovery \"" + instance.textField1.getText() + "\"");
        setCurTask("Done!", 3, 3);
    }

    public static void setCurTask(String taskName, int total, int cur)
    {
        instance.currentAction.setText("Current Task: " + taskName);
        instance.progressBar1.setMaximum(total);
        instance.progressBar1.setValue(cur);
        instance.frame.repaint();
        instance.statusLog.setText(instance.statusLog.getText() + taskName + "\n");
    }

    public static void unTar(File infile, File outfile)
    {
        // damn you java for not having native tar support

        setCurTask("UnTARing " + infile.getName(), 1, 0);

        try
        {
            TarArchiveInputStream debInputStream = new TarArchiveInputStream(new FileInputStream(infile));
            TarArchiveEntry entry = null;

            while ((entry = (TarArchiveEntry) debInputStream.getNextEntry()) != null)
            {
                final File outputFile = new File(outfile, entry.getName());

                if (entry.isDirectory())
                {
                    if (!outputFile.exists())
                    {
                        outputFile.mkdirs();
                    }
                } else
                {
                    OutputStream outputFileStream = new FileOutputStream(outputFile);
                    IOUtils.copy(debInputStream, outputFileStream);
                    outputFileStream.flush();
                    outputFileStream.close();
                }
            }

            debInputStream.close();
        }catch (Exception e) {e.printStackTrace();} //TODO: better error handling

        setCurTask("UnTARing " + infile.getName(), 1, 1);
    }

    public static void unGZIP(File infile, File outfile)
    {
        setCurTask("UnGZIPing " + infile.getName(), 1, 0);

        try
        {
            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outfile));
            GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(infile));

            int BUFFER = 1024*1024*1024;

            int dataSize = 0;
            byte[] data = new byte[BUFFER];
            while((dataSize = gzipInputStream.read(data, 0, BUFFER)) != -1)
            {
                outputStream.write(data, 0, dataSize);
            }

            outputStream.flush();
            outputStream.close();

            gzipInputStream.close();
        }catch (Exception e) {e.printStackTrace();} // TODO: add better error handling

        setCurTask("UnGZIPing " + infile.getName(), 1, 1);
    }

    public static  void unzip(File infile, File outPath)
    {
        int BUFFER = 1024 * 1024 * 1024;

        setCurTask("UnZIPing: " + infile.getName(), 1, 0);

        try{
            BufferedOutputStream out;
            ZipInputStream  in = new ZipInputStream(new FileInputStream(infile));
            ZipEntry entry;
            boolean isDirectory=false;
            while((entry = in.getNextEntry()) != null){
                int count;
                byte data[] = new byte[BUFFER];
                // write the files to the disk
                String entryName = entry.getName();
                File newFile = new File(outPath, entryName);
                if(entryName.endsWith("/")){
                    isDirectory=true;
                    newFile.mkdir();
                }else{
                    newFile.createNewFile();
                }
                if(!isDirectory){
                    out = new BufferedOutputStream(new FileOutputStream(newFile),BUFFER);
                    while ((count = in.read(data,0,BUFFER)) != -1){
                        out.write(data,0,count);
                    }

                    out.flush();
                    out.close();
                }
                isDirectory=false;
            }
            in.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }

        setCurTask("UnZIPing: " + infile.getName(), 1, 1);
    }
}
