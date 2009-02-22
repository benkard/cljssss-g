/* just an experiment - should be implemented in clojure later */

import java.io.*;
import javax.tools.*;
 
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.UUID;
import java.util.Properties;
 
public class Geniface {

    class ByteClassLoader extends ClassLoader{
	String name; byte[] b;
	ByteClassLoader (String name, byte[] b) {
	    this.b=b;
	    this.name=name;
	}
	Class getTheClass() {
	    return defineClass(name, b, 0, b.length);
	}
    }

    private String temp_prefix;
    private String my_uuid;
 
    private String GeneratedText;
 
    public Geniface (String temp_prefix) {
	this.temp_prefix = temp_prefix;
	this.my_uuid = "myuid" + UUID.randomUUID().toString().replace('-', '_');
	this.GeneratedText =
	    "public interface " + my_uuid + " {";
    }
 
    public Geniface () {
	this("/tmp");
    }
 
    public void addMethod (String name, int ObjectArgCount, boolean ret) {
	/* Add a Method with ObjectArgCount arguments which either
	 * returns void if !ret, or returns an Object if ret */
	GeneratedText +=
	    (ret ? "\n\tObject " : "\n\tvoid ") + name + "(";
	for (int i = 0; i < ObjectArgCount; i++) {
	    GeneratedText += (i == 0 ? "" : ", ") + "Object arg" + i;
	}
	GeneratedText += ");";
    }
 
    public String getSourceString () {
	return GeneratedText + "}";
    }
 
    public static byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);
 
        // Get the size of the file
        long length = file.length();
 
        if (length > Integer.MAX_VALUE) {
            // File is too large
        }
 
        // Create the byte array to hold the data
        byte[] bytes = new byte[(int)length];
 
        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
               && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }
 
        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file "+file.getName());
        }
 
        // Close the input stream and return bytes
        is.close();
        return bytes;
    } 
 
    public Class compileToClass () {
	try {
	String javaname = temp_prefix + "/" + my_uuid + ".java";
	String classname = temp_prefix + "/" + my_uuid + ".class";

	FileOutputStream out = new FileOutputStream(javaname);
	out.write(getSourceString().getBytes());
	out.close();
 
	File [] files1 = new File[]
	    { new File(javaname) };
 
	JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
	DiagnosticCollector<JavaFileObject> diagnostics = new 
	    DiagnosticCollector<JavaFileObject>();
	StandardJavaFileManager fileManager = 
	    compiler.getStandardFileManager(diagnostics, Locale.GERMANY,
					    Charset.forName("UTF-8"));
 
	Iterable<? extends JavaFileObject> compilationUnits1 = 
	    fileManager.getJavaFileObjectsFromFiles(java.util.Arrays.asList(files1));
	compiler.getTask(null, fileManager, diagnostics, null, null, 
compilationUnits1).call();
 
	for (Diagnostic diagnostic : diagnostics.getDiagnostics())
	    {
			System.out.println(diagnostic);
	    }
 
	fileManager.close();

	return new ByteClassLoader(my_uuid, getBytesFromFile (new File(classname))).getTheClass();
	}
	catch (IOException exn1) {
	    System.out.println("IOExn");
	}
 
	return null;
 
    }
 
    public static void main (String[] args) {
	Geniface iface1 = new Geniface();
	iface1.addMethod("Hallo", 10, false);
	iface1.addMethod("Fuck", 2, true);
	iface1.addMethod("valuesofbetawillgiverisetodom", 1, true);
	iface1.addMethod("nilpferd", 0, false);
	System.out.println(iface1.getSourceString());
	System.out.println(iface1.compileToClass());
    }
}