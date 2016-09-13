package simpletcpproxy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.junit.Test;

import com.beef.easytcp.asyncserver.test.TestTcpProxyServer;
import com.beef.easytcp.asyncserver.test.proxy.TcpProxyServer;
import com.beef.easytcp.asyncserver.test.proxy.config.BackendSetting;
import com.beef.easytcp.asyncserver.test.proxy.config.TcpProxyServerConfig;

import MetoXML.XmlDeserializer;
import MetoXML.XmlSerializer;
import MetoXML.Base.XmlParseException;
import MetoXML.Util.ClassFinder;

public class MainEntry {
	private final static Logger logger = Logger.getLogger(MainEntry.class);

	/**
	 * 
	 * @param args args[0]:config file path
	 */
    public static void main(String[] args) {
    	try {
    		checkArgs(args);
    		
        	TcpProxyServerConfig config = loadConfig(args[0]);
    		
        	runProxyServer(config);
    	} catch (Throwable e) {
    		logger.error(null, e);
    	}
    }
    
    private static void checkArgs(String[] args) {
    	if(args.length != 1) {
    		System.out.println("Usage help:");
    		System.out.println("./startup.sh conf/TcpProxyServerConfig.xml");
    		
    		throw new IllegalArgumentException("Arguments incorrect!");
    	}
    } 

    private static void runProxyServer(TcpProxyServerConfig config) throws IOException {
        final TcpProxyServer proxyServer = new TcpProxyServer(config);
        proxyServer.start();
        
		//handle kill signal ----------------------
        Runtime.getRuntime().addShutdownHook(new Thread() {
        	@Override
        	public void run() {
        		try {
        			logger.info(" received kill signal. ------");
        			//System.out.println(getProgramName() + " received kill signal. ------");
        			
        			proxyServer.close();
        		} catch(Throwable e) {
        			e.printStackTrace();
        			logger.error(null, e);
        		}
        	}
        });
        
        proxyServer.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }
    
    private static TcpProxyServerConfig loadConfig(String configPath) 
    		throws InvocationTargetException, IllegalAccessException, InstantiationException, NoSuchMethodException, XmlParseException, IOException {
    	System.out.println("TcpProxyServerConfig path:" + configPath);
    	
    	File file = new File(configPath);
    	XmlDeserializer xmlDes = new XmlDeserializer();
    	
    	return (TcpProxyServerConfig) xmlDes.Deserialize(
    			file.getAbsolutePath(), 
    			TcpProxyServerConfig.class, XmlDeserializer.DefaultCharset, 
    			new ClassFinder() {
    				
					@Override
					public Class<?> findClass(String className) throws ClassNotFoundException {
						if(className.equals(BackendSetting.class.getSimpleName())) {
							return BackendSetting.class;
						} else {
							return null;
						}
					}
				});
    }

	@Test
	public void test1() {
		TcpProxyServerConfig config = TestTcpProxyServer.buildConfig();
		
		File file = new File(TcpProxyServerConfig.class.getSimpleName() + ".xml");
		
		try {
			XmlSerializer xmlSer = new XmlSerializer();
			xmlSer.Serialize(file.getAbsolutePath(), config, config.getClass(), XmlDeserializer.DefaultCharset);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
    
}
