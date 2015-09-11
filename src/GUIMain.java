import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarUtils;
import org.apache.commons.compress.utils.IOUtils;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
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
        instance.flashFactoryButton.addActionListener((al) -> flashFactory());
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

        for(File file : extractedFolder.listFiles())
        {
            String name = file.getName();

            if(!name.endsWith(".zip") && !name.endsWith(".img"))
            {
                if(!file.delete())
                {
                    System.out.println("Couldn't delete unnecessary file, removing on exit");
                    file.deleteOnExit();
                }
            }
        }

        // move everything from the extracted folder up a level, makes the flashing a bit easier

        for(File file : extractedFolder.listFiles())
        {
            try
            {
                Files.move(file.toPath(), new File(out, file.getName()).toPath());
            } catch (IOException e) {e.printStackTrace();} //TODO: better error handling
        }

        // get rid of all the other files I don't need, such as the tar archive

        for(File file : out.listFiles())
        {
            String name = file.getName();

            if(!name.endsWith(".zip") && !name.endsWith(".img"))
            {
                if(!file.delete())
                {
                    System.out.println("Couldn't delete unnecessary file, removing on exit");
                    file.deleteOnExit();
                }
            }
        }

        if(true)
        return;

        String imageZip = "";

        for(File f : out.listFiles())
        {
            if(f.getName().endsWith(".zip"))
            {
                imageZip = f.getPath();
            }
        }

        if(imageZip.isEmpty())
        {
            setCurTask("No image zip found, flash failed", 1, 1);
            return;
        }

        File imageOut = new File(out, "image - extracted");

        try
        {
            imageOut.mkdirs();
        }catch(Exception e) {e.printStackTrace();}

        unzip(new File(imageZip), imageOut);
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
        }catch (Exception e) {e.printStackTrace();}; //TODO: better error handling
    }

    public static void unGZIP(File infile, File outfile)
    {
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
    }

    public static  void unzip(File infile, File outPath)
    {
        int BUFFER = 2048;

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
    }


}
