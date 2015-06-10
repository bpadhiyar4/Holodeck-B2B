/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.holodeckb2b.common.workers;

import org.holodeckb2b.common.workers.DirWatcher;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.holodeckb2b.common.workerpool.TaskConfigurationException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Sander Fieten <sander@holodeck-b2b.org>
 */
public class DirWatcherTest {
    
    DirWatcherImpl  instance;
    String          basePath;
    File            testDir;
    
    public DirWatcherTest() {
    }
    
    @Before
    public void setUp() {
        instance = new DirWatcherImpl();
        basePath = this.getClass().getClassLoader().getResource("dirwatcher").getPath();
        
        testDir = new File(basePath + "/checkdir");
        
        try {
            FileUtils.deleteDirectory(testDir);
            FileUtils.copyDirectory(new File(basePath + "/clean"), testDir);
        } catch (IOException ex) {
            Logger.getLogger(DirWatcherTest.class.getName()).log(Level.SEVERE, null, ex);
        }        
        
        basePath += "/checkdir";
    }
    
    @After
    public void tearDown() {       
        try {
            FileUtils.deleteDirectory(testDir);
        } catch (IOException ex) {
            Logger.getLogger(DirWatcherTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Test of setParameters method, of class DirWatcher.
     */
    @Test
    public void testSetParameters() {
        System.out.println("setParameters");
        Map<String, String> parameters = new HashMap<String, String>();
        
        parameters.put("watchPath", basePath);
        
        try {
            instance.setParameters(parameters);
        } catch (TaskConfigurationException ex) {
            Logger.getLogger(DirWatcherTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("Configuration failed");
        }
    }

    /**
     * First test, all files look new 
     */
    @Test
    public void testFirstRun() {
        
        testSetParameters();
        instance.run();
        
        assertEquals(4, instance.c);               
    }

    /**
     * Second test, add a file
     */
    // TODO test failed, correct and add comment
    /*
    @Test
    public void testAddToEnd() {
        
        testSetParameters();
        instance.run();
        
        assertEquals(4, instance.c);               
        
        String opath = basePath + "/ignore-me/Foto-5.JPG";
        String npath = basePath + "/Foto-5.JPG";
        
        try {
            FileUtils.copyFile(new File(opath), new File(npath));
        } catch (IOException ex) {
            Logger.getLogger(DirWatcherTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        instance.run();
        assertEquals(1, instance.c);               
    }
    */

    /**
     * Delete first
     */
    @Test
    public void testDeleteFirst() {
        
        testSetParameters();
        instance.run();
        
        assertEquals(4, instance.c);               
        
        String dpath = basePath + "/Foto-1.jpg";
        
        new File(dpath).delete();
        
        instance.run();
        assertEquals(1, instance.c);               
    }    
    
    /**
     * Delete second, add to end
     */
    // TODO test failed, correct and add comment
    /*
    @Test
    public void testDeleteAndAdd() {
        
        testSetParameters();
        instance.run();
        
        assertEquals(4, instance.c);               
        
        String dpath = basePath + "/Foto-2.jpg";
        
        new File(dpath).delete();
        
        String opath = basePath + "/ignore-me/Foto-5.JPG";
        String npath = basePath + "/Foto-5.JPG";
        try {
            FileUtils.copyFile(new File(opath), new File(npath));
        } catch (IOException ex) {
            Logger.getLogger(DirWatcherTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        instance.run();
        assertEquals(2, instance.c);               
    }    
    */
    
    public class DirWatcherImpl extends DirWatcher {
        public int c = 0;
        
        public void run() {
            c = 0;
            super.run();
        }
        
        public void onChange(File f, Event event) {
            System.out.println("Change [" + event.name() + "] reported for " + f.getAbsolutePath());
            c++;
        }
    }
}