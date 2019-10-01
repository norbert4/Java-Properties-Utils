package de.noschu.shsutils;
/*
 * Copyright (c) 2018,2019, Norbert Schultheis. All rights reserved.
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 * This code is distributed in the hope that it will be useful, but without
 * any warranty or fitness for a particular purpose.
 * 
 * You are welcome to send suggestions, criticisms or bugs to: noschu@web.de
 */
import java.io.*;

import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <h3>Misc. functions for Properties containers and .properties files</h3>
 * Also supports reading/writing of .ini files and synchronizing resource bundle files.
 * @author Copyright (c) 2018,2019 <a href="mailto:noschu@web.de">Norbert Schultheis</a> All Rights Reserved
 * @version 1.04 2019/09/30
 */
public class Prop
{
	// The following getLogger() call will use a Java Util Logging (JUL) config file, which you can set 
    // in main() like:
    // String cwd = Paths.get("").toAbsolutePath().toString(); 
    // String my_jul_config = cwd + File.separator + "etc" + File.separator + "my_logging.properties";
	// System.setProperty("java.util.logging.config.file",my_jul_config); 
	
	/*private */static final Logger logger = Logger.getLogger(Prop.class.getName());
	
/**
* Get the first comment lines of a .properties file
* @param propfile The properties file to be examined 
* @return List of comment lines, may be empty
* @throws IOException For each type of error an IOException is thrown
* @since Last change: 2019.08.25
*/
public static List<String> getHeaderLines(Path propfile) throws IOException
{  
    BufferedReader br = null; 
try
{      
    Charset cs = getCharset(propfile);  //if (isUTF8(propfile)) cs = StandardCharsets.UTF_8;
    String tmp = cs.toString();
    if (tmp.startsWith("UTF-32")) throw new IOException("Charset " + cs + " is not supported");

    br = Files.newBufferedReader(propfile,cs);
 
    if (cs != StandardCharsets.ISO_8859_1)
    {   br.mark(4); // each BOM results in codepoint 0xFEFF which is one java char (2 bytes) 
        if ('\uFEFF' != br.read())  br.reset(); // SKIP THE BOM OR REWIND
    }  
    List<String> headLines = new ArrayList<String>();
    String line; char c = 'x'; int pos;
    while((line = br.readLine()) != null)  
    {    
       if (line.length() < 1) continue;
       
       for (pos=0;pos < line.length();pos++) // STEP: 1.1) Skip white spaces in front of the key - if any 
       {   c = line.charAt(pos);  //if (!Character.isWhitespace(c)) break; sind zu viele
           if (c != ' ' && c != '\t') break; 
       }  
       if (c != '#' && c !='!') break; 
       headLines.add(line.substring(pos)); 
    }    
    return headLines;

} catch (Exception e) 
{   throw new IOException("getHeaderLines(): " + e + ", (file: " + propfile + ")");  
} finally
{   try { br.close(); } catch (Exception e) {}  
}
} // ----------------------end of getHeaderLines()
	
/**
 * Wrapper for Properties.load(Reader): Loads a .properties file into a Properties container.
 * The .properties file will be encoded depending on it's format: UTF-8,UTF-16BE,UTF-16LE  
 * or ISO-8859-1 - UTF-32 is not supported<br>
 * Unfortunately (for historical reasons) Oracle's format specification of a .properties 
 * file allows control characters such as newline '\\n', carriage return '\\r', Tab '\\t', 
 * formfeed '\\f', unicode chars and mutated vowels in a <b>key's name</b>! 
 * To avoid such key names see: {@link #loadProper(Path, Properties...)}
 * Remember - in .properties files the backslash
 * character in a path name must be escaped as a double backslash. For example: path=c:\\docs\\doc1. 
 * @param propfile Path name to properties file e.g. a user configfile
 * @param defProps Option: Default properties e.g. a default system configfile
 * @return Properties container
 * @throws IOException On error, e.g. bad key name or invalid unicode sequence or 'propfile' does not exist
 * @since Last change: 2019.09.19 
 */
public static Properties loadWrapper(Path propfile,Properties... defProps) throws IOException
{ 
    final String fn = "loadWrapper()"; 

    Properties props = null;
    if (defProps.length > 0) props = new Properties(defProps[0]); 
    else                     props = new Properties();   
       
    BufferedReader br = null;       Charset cs = StandardCharsets.ISO_8859_1;	   String msg,tmp;
    
try
{   
    cs = getCharset(propfile);
    tmp = cs.toString();
    if (tmp.startsWith("UTF-32")) throw new IOException("Charset " + cs + " is not supported");
    
    logger.log(Level.FINER,"The .properties file will be read using encoding {0} : {1}",new Object[] {cs,propfile});
    
    br = Files.newBufferedReader(propfile,cs);
    
    if (cs != StandardCharsets.ISO_8859_1)
    {   br.mark(4); // each BOM results in codepoint 0xFEFF which is one java char (2 bytes) 
        if ('\uFEFF' != br.read())  br.reset(); // SKIP THE BOM OR REWIND
    }  
    props.load(br);
    return props;
    
} catch (Exception e)
{  
    if (e instanceof IOException) msg = e.getMessage(); else msg = e.toString();
    throw new IOException(fn + ": " + msg + " (File: " + propfile + ")"); 
       
} finally
{ try { br.close(); } catch (Exception ee) {} }
} //--------------------- end of loadWrapper()


/* 
 * Loads a .properties file -in a simplified format- into a Properties container according to it's
 * character encoding: UTF-8,UTF-16BE,UTF-16LE (UTF-32 is not supported).
 * This method ensures that all key names contain no control characters, no backslash and no 'mutated vowels'.<br>
 * What does 'simplified format' means ? For historical reasons, Oracle's format specification of a .properties 
 * file allows control characters such as newline '\\n', carriage return '\\r', TABs '\\t', 
 * formfeed '\\f' in a <b>key's name<b> ! In general, a simplified format is used today (key = value) 
 * - which can be considered a subset of the official format capabilities.
 * There are only a few restrictions for the keys in the simplified properties files:<br>
 * <ul>
 * <li> Key name and key value are seperated by an = char (not a colon or space)<br>
 * <li> Allowed characters for the key name are all ASCII-chars in range U+0020 - U+007E(~) but \\ and =
 * <li> Because key names are trimmed, they can not start or end with whitespaces.
 * </ul> 
 * Reading the property <b>values<b> complies with the Oracle specification including continuation lines,
 * unicode escape sequences e.g. \\u263A (smiley) and unicode surrogate pairs like "\\ud83d\\udc1d" (honey bee 
 * beyond 0xFFFF).  \\n, \\t \\r \\f will be expanded to the representing control char -other escpes are ignored (e.g. backspace).
 * Remember - in .properties files the backslash character in a path name must be escaped as a double backslash. 
 * For example: path=c:\\docs\\doc1. 
 * @param propfile Path name to properties file e.g. a user configfile
 * @param defProps Option: Default properties e.g. a default system configfile
 * @return Properties container
 * @throws IOException On error, e.g. bad key name or invalid unicode sequence or 'propfile' does not exist
 * @since Last change: 2019.09.23 
 */
public static Properties loadProper(Path propfile,Properties... defProps) throws IOException
{ 
    final String fn = "loadProper()"; 
    Properties props = null; char c; int len,st; // st=startpos
    BufferedReader br = null; String line="",contline,tmp; 
    
try
{   Charset cs = getCharset(propfile); //if (isUTF8(propfile)) cs = StandardCharsets.UTF_8;
    tmp = cs.name();
    if (tmp.startsWith("UTF-32")) throw new IOException("Charset " + cs + " is not supported");

    logger.log(Level.FINER,"The .properties file will be read using encoding {0} : {1}",new Object[] {cs,propfile});

    br = Files.newBufferedReader(propfile,cs);
    
    if (cs != StandardCharsets.ISO_8859_1)
    {   br.mark(4); // each BOM results in codepoint 0xFEFF which is one java char (2 bytes) 
        if ('\uFEFF' != br.read())  br.reset(); // SKIP THE BOM OR REWIND
    }  
    if (defProps.length > 0) props = new Properties(defProps[0]); 
    else                     props = new Properties();   
     
    while((line = br.readLine()) != null)  
    {     
       //--- STEP: 1) Read a natural line, ignore empty or comment lines   
       if ( (len = line.length()) == 0) continue;   
       st = 0;
       c = 'x';
       for ( ;st < len;st++) // STEP: 1.1) Skip white spaces in front of the key - if any 
       {   c = line.charAt(st);
           if (c != ' ' && c != '\t' /* && c != '\f'*/ ) break; // \f \u000c and ALT-12 (FORMFEED) does not work as specifid by oracle
       }                                                        // formmeed terminates a key, and will not be trimmed
       if (st >= len || c == '#' || c == '!') continue; // empty line or comment line found    
       if (st > 0) line = line.substring(st);
       
       //--- STEP: 2) Read more continuation line(s) -if any
       while(hasContLine(line)) 
       {         
          line = line.substring(0,line.length()-1); // truncate the last backslash        
          if ( (contline = br.readLine()) == null) break; // end of file   
          for (st=0; st < contline.length(); st++)  //--- skip the front of the contline
          {  c = contline.charAt(st);
             if (c != ' ' && c != '\t') break;
          } 
          line += contline.substring(st);          
       }   
       //======================================
       //--- STEP: 3) Parse the logical line 
       //======================================   
       String[] keyAndValue = parseLineProper(line); // throws IOEx.
       
       logger.log(Level.FINER,"Key: \"{0}\" , Value: \"{1}\"",new Object[] { keyAndValue[0],keyAndValue[1] });
       
       //--- STEP: 4) Set the value
       props.setProperty(keyAndValue[0],keyAndValue[1]);
    }  //------------------------------- next line
 
    return props;
    
} catch (Exception e)
{  
    String msg; if (e instanceof IOException) msg = e.getMessage(); else msg = e.toString();
    throw new IOException(fn + ": " + msg + ", line: '"+line+"', (File: " + propfile + ")"); 
    
} finally
{ try { br.close(); } catch (Exception ee) {} }
} //---------------------------------------------------- end of loadProper()

/**
 * Creates a Properties container from '.ini' file (any char encoding). The capture name -inside the brackets- is used as prefix of the keys
 * in this capture. For example:<br>
 * [Startup] <br>
 * FreeDiskSpace=3435 <br>
 * UsedDiskSpace=647483<br>
 * results in properties: Startup.FreeDiskSpace = 3435  Startup.UsedDiskSpace = 64783
 * The .ini file may encoded with any of the StandardCharsets e.g. UTF-(
 * @param inifile The pathname 
 * @return Properties container
 * @throws IOException On error
 * @see #storeToInifile(Properties, Path)
 * @since Last change: 2019.05.29  
 */
public static Properties loadFromIniFile(Path inifile) throws IOException 
{    
    Properties props = new Properties();  
    BufferedReader br = null;   
    Charset cs;
    String line="",key,val,category="root";
    int pos,linno = 0;   char c;
try
{
    cs =  CharsetUtils.getCharset(inifile); 

    br = Files.newBufferedReader(inifile,cs);
    while ((line = br.readLine()) != null)   
    {   
        linno++;
        if (line.isEmpty()) continue; 
        c = line.charAt(0); 
        if (c == ';' || c == '#' || c == '/') continue; 
        if (c == '[')
        {
            if ( (pos = line.indexOf(']')) == -1)
            {  logger.log(Level.INFO,"Missing closing ] in line {0}: {1}",new Object[]{linno,line});
               continue; //category = line.substring(1).trim();  
            }   
            category = line.substring(1,pos).trim();
            continue;
        }
        if ( (pos = line.indexOf('=')) == -1) continue;
                  
        key = line.substring(0,pos).trim();
        val = line.substring(pos+1);
        props.setProperty(category + "." + key,val);
    }  
    br.close();  
    
} catch (Exception e)  // IOException oder FileNotFoundException
{  
    try { br.close(); } catch (IOException e2) { } // NEW: 2017.03.17 
    throw new IOException("loadIniFile(): " + e.getMessage() + " (file '" + inifile + "), line: " + line);
}
    return props;
} //--------------------------------------- end of loadIniFile()


/**
 * Loads a textfile (without .properties layout) into a Properties container. 
 * As in contrast to Properties.load() the values are trimmed (e.g. key = \t value \t  ) if they are not enclosed with
 * quotes like:. key = "  foo bar  ". path names contains single backslashes as directory separator
 * like: C:\temp\log instead of C:\\temp\\log.
 * No expanding is done for TAB Newline Carriage Return and Formfeed
 * Its not recommended to use quotes for values without leading or trailing blanks (e.g. key = "value")- but you can.  
 * Lines starting with # or without containing a '=' are ignored.
 * @param file Path name to the file
 * @return Properties container
 * @throws IOException On error
 * @since Last change: 2019.06.20  
 */
public static Properties loadTextfile(Path file) throws IOException
{ 
    BufferedReader br = null;
try
{  
    String line,key,val;   char c;  int pos,len,linno=0;    
    Charset cs =  getCharset(file);
    String tmp = cs.name();
    if (tmp.startsWith("UTF-32")) throw new IOException("Charset " + cs + " is not supported");

    br = Files.newBufferedReader(file,cs); 
    
    Properties cfg = new Properties();   
    
    while ((line = br.readLine()) != null)   
    {   
        linno++;
        if (line.isEmpty()) continue; 
        c = line.charAt(0);
        if (c == '#') continue;  
        if ( (pos = line.indexOf('=')) == -1) continue;        
        key = line.substring(0,pos).trim(); // if (key.isEmpty()) continue;   
        
        val = line.substring(pos+1).trim(); // key = value
        len = val.length();
        if (len >= 2 && val.charAt(0) == '"') // sonst wuerde auch der wert mit nur einem  "  falsch interpretiert wreden
        {                   
            if (val.charAt(len-1) == '"')  val = val.substring(1,len-1);  
            else  
             logger.log(Level.WARNING,"Value in line {0} starts with a \" however not closed with a \": \"{1}\" , file: {2}",
                    new Object[] {linno,line,file});
        }
        cfg.setProperty(key,val);
    }
    br.close();  
    return cfg;

} catch (Exception e)
{  
    try { br.close(); } catch (IOException e2) { }   
    throw new IOException("loadTextFile: " + e.getMessage() + " (File: '" + file + "')");
}
} //--------------------- end of loadTextFile()

/**
 * Wrapper for Properties.store() to store a properties container in encoding UTF-8
 * @param props The Properties container
 * @param outfile The output file
 * @param comment A comment, may be null or empty
 * @throws IOException on error
 * @since Last change: 2019.09.22 
 */
public static void storeWrapper(Properties props,Path outfile,String comment) throws IOException
{ 
    BufferedWriter bw = null;
try
{   bw = Files.newBufferedWriter(outfile,StandardCharsets.UTF_8);   
    props.store(bw,comment);
    bw.close();
} catch (Exception e)
{   
    try { bw.close(); } catch (Exception ee ) {}
    String msg;   if (e instanceof IOException) msg = e.getMessage(); else msg = e.toString();
    throw new IOException("Prop.storeWrapper(): " + msg + " (file: " + outfile + ")"); 
}    
} //--------------------- end of storeWrapper()



/**
 * Stores a Properties container to an UTF-8 .properties file.
 * storeProper() is the counterpart to loadProper(): It ensures that no control characters are included in the keynames.
 * Optionally - the output may be sorted by key.
 * @param props The Properties container, must not be null
 * @param propfile The output file, must not be null
 * @param title A comment, may be null or empty
 * @param sort Option: sort If true the output is sorted by key
 * @throws IOException On error
 * @since Last change: 2019.09.22
 */
public static void storeProper(Properties props,Path propfile,String title,boolean... sort) throws IOException
{ 
    boolean sortit = false; if (sort.length > 0) sortit = sort[0];   
    final String EOL = System.getProperty("line.separator","\n");
    BufferedWriter bw = null;
  
try
{    
    bw = Files.newBufferedWriter(propfile,StandardCharsets.UTF_8);
    if (title != null && !title.isEmpty())  bw.write("#" + title + EOL);

    Date date; //SimpleDateFormat sdf; String datestr;
    try
    {   date = new Date();  //sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm"); datestr = sdf.format(date);  
        bw.write("#" + date + EOL);   
    } catch (Exception e) { /*datestr = "1970-01-01 00:00";*/ }

    Set<String> keys = props.stringPropertyNames(); 
    List<String> keylist = new ArrayList<String>();    
    keylist.addAll(keys);
    if (sortit) java.util.Collections.sort(keylist); 
    String v;  char c;   int len; boolean changed;
    StringBuffer sb = new StringBuffer();

    for (String k : keylist)   
    {    
        if (k.contains("=")) throw new IOException("Invalid key, contains a '=' char: '"+k+"'");
        if (k.contains("\\")) throw new IOException("Invalid key, contains a '\\' char: '"+k+"'");
        len = k.length();
        for (int i=0;i < len;i++)
        { if (Character.isISOControl(k.charAt(i))) throw new IOException("Invalid key, contains a Control character: '"+k+"'");
        }
        v = props.getProperty(k,"");
        changed = false; 
        sb.setLength(0);
        for (int i =0;i < v.length();i++)
        {
           c = v.charAt(i);
           switch(c)
           {
           case '\\': sb.append("\\\\"); changed = true; break;
           case '\n': sb.append("\\n"); changed = true; break;
           case '\r': sb.append("\\r"); changed = true; break;  
           case '\f': sb.append("\\f"); changed = true; break; 
           case '\t': sb.append("\\t"); changed = true; break;
           case '=': sb.append("\\="); changed = true; break; // Compatibility to Properties.store()
           case ':': sb.append("\\:"); changed = true; break; // Compatibility to Properties.store()
           default:  sb.append(c);
           }
        }
        if (changed) v = sb.toString();
      
        if (v.length() > 0)
        { c = v.charAt(0);
          if (c == ' '  || c == '\t') v = "\\" + v; // blanks and tabs at the beginning of a value must be escaped
        }
        bw.write(k + "=" + v + EOL);
    }
    keylist.clear();    // // IMPORTANT: Beliebter fehler keys.clear() damit wird die map leer !!!
  
} catch (Exception e)
{  throw new IOException("storeProper(): " + e.getMessage() + " (outfile: " + propfile + ")");    
} finally
{ try { bw.close(); } catch (Exception ee) {}  
}
} //--------------------- end of storeProper() 

/**
 * Stores a properties container to a .ini file. The container should be created via loadIniFile().
 * The first part of a key before a dot -e.g. user.homedir - will be stored in capture [user].
 * If no dot was found in the key name of such keys are is stored in capure [root]
 * @param props The properties to be stored
 * @param inifile The output .ini file to be created
 * @throws IOException On error
 * @see "loadIniFile()"
 * @since Last change: 2019.05.29  
 */
public static void storeInifile(Properties props,Path inifile) throws IOException
{
    if (props == null) return; //throw new IllegalArgumentException(fn + ": Invalid arg. 'inifile' (null or empty)");
      
    BufferedWriter ofs = null; 
    String val,cur_category,category;  int pos;
    Properties noCapture = new Properties();
    List<String> out = new ArrayList<>();
    try
    {              
        Set<String> keys = props.stringPropertyNames(); 
        List<String> keylist = new ArrayList<String>();    
        keylist.addAll(keys);
        java.util.Collections.sort(keylist); 
        
        cur_category = "";
        for (String key : keylist)
        {
           val = props.getProperty(key,"");   
           if ( (pos = key.indexOf('.')) == -1) // will stored as [root]
           {       noCapture.setProperty(key,val); 
                   continue;
           }
           category = key.substring(0,pos);    
           if (!cur_category.equals(category))  // a category was found
           {             
               out.add("[" + category + "]");
               cur_category = category;
           }
         //  ofs.write(key.substring(pos+1) + "="  + val + EOL);   
           out.add(key.substring(pos+1) + "="  + val);
        } 
        keylist.clear();
        
        if (noCapture.size() > 0) 
        {
            out.add("[root]");
            keys = noCapture.stringPropertyNames(); //// alle keys in keylist ablegen und sortieren
            keylist.clear();    
            keylist.addAll(keys);
            java.util.Collections.sort(keylist); 
            for (String key : keylist)
            {
                out.add(key+"="+noCapture.getProperty(key,""));
            }
            keylist.clear();
        }      
        ofs = Files.newBufferedWriter(inifile,StandardCharsets.UTF_8); 
        
        for (String s : out) ofs.write(s + "\r\n");
        ofs.close(); 
        out.clear();
        
    } catch (Exception e)
    {
        try { ofs.close(); } catch (IOException e2) {}   // NEW: 2017.03.17          
        throw new IOException("storeIniFile() failed: " + e.toString() + ", (file: "+ inifile + ")");
    }    
} //--------------- end of storeIniFile()


/**
 * Retrieves a property value for a key as an int value
 * @param props The Properties container
 * @param key The key 
 * @param defval The default value if the key doesnt exist
 * @return int value of key
 */
public static int getPropertyAsInt(Properties props,String key,int defval)
{
   try { return Integer.parseInt(props.getProperty(key,"")); } catch(Exception e) {}
   return defval;
} //-------------------------------- end of getPropertyAsInt()

/**
* Retrieves a property value as a boolean value. true yes on 1 is considered 'true. false no off 0 is considered 'false'
* @param props The Properteis container
* @param key The key
* @param defbool The default value if the key doesnt exist or does not represent an boolean
* @return The boolean value of the key
* @since Last change: 2018.01.18 
*/
public static boolean getPropertyAsBool(Properties props,String key,boolean defbool)
{
     if (props==null || key==null) return defbool;
     String s = props.getProperty(key,"").trim().toLowerCase();
     if (s.isEmpty()) return defbool;
     if ("true yes on 1".contains(s)) return true;
     if ("false no off 0".contains(s)) return false;
     return defbool;
} //-------------------------------- end of getPropertyAsBool()



/**
 * Wrapper for Properties.loadFromXML() method to load a properties object as stored with storeToXML()
 * defined in: "http://java.sun.com/dtd/properties.dtd"
 * @param xmlfile The input file
 * @return The properties container
 * @throws IOException on error
 * @since Last change: 2016.12.01 
 */
public static Properties loadFromXMLWrapper(Path xmlfile) throws IOException
{ 
    /*Buffered*/InputStream ifs=null; // hier kann kein Writer objekt verwendet werden
try
{  
    Properties props = new Properties();
    ifs = new BufferedInputStream(new FileInputStream(xmlfile.toString()));      
    
    props.loadFromXML(ifs); // leider byte basierend
    return props;
}
catch (Exception e)
{ 
    throw new IOException("Props.loadFromXMLWrapper(): " + e.getMessage() + " (xmlfile: '" + xmlfile + "')"); 
}
finally
{  try { ifs.close(); } catch ( Exception e ) { } 
}      
} //--------------------- end of loadFromXMLWrapper()



/**
 * Wrapper for Properties.storeToXML() method to store a properties container to a properties .xml file
 * as defined in: "http://java.sun.com/dtd/properties.dtd". UTF-8 encoding is used.
 * @param props The Properties container
 * @param outfile The output file
 * @param comment A comment, may be null or empty
 * @throws IOException on error
 * @since Last change: 2017.06.03 
 */
public static void storeToXMLWrapper(Properties props,Path outfile,String comment) throws IOException
{ 
    /*Buffered*/OutputStream ofs=null; // hier kann kein Writer objekt verwendet werden
try
{  
    ofs = new BufferedOutputStream(new FileOutputStream(outfile.toString()));  
    props.storeToXML(ofs,comment,"UTF-8"); 
    ofs.close();
    
} catch (Exception e)
{   try { ofs.close(); } catch (Exception ee) {}
    String msg;
    if (e instanceof IOException) msg = e.getMessage(); else msg = e.toString();
    throw new IOException("Prop.storeToXMLWrapper(): " + msg + ", (outfile: " + outfile + ")"); 
}    
} //--------------------- end of storeToXMLWrapper()




/**
* Syncronizes a resource bundle client file e.g. msg_de.properties from your master resource bundle file e.g. msg_us.properties.
* Imagine that you have made many changes to the master file and now need to take over and translate them into other 
* languages. In other words - this method synchronizes the client file based on the master file.
* New properties in the client file looks like:<br>
* MSG_FILE_NOT_FOUND = mastervalue + "----TODO: TRANSLATE ME".
* Extra property keys in clientfile are renamed to a comment line like<br>
* #_OBSOLETED_" + oldkey = oldval
* @param propFileMaster Message file e.g. us.properties with entries like: MSG_FILE_NOT_FOUND = File not found
* @param propFileClient Message file e.g. de.properties with entries like: MSG_FILE_NOT_FOUND = Datei nicht gefunden
* @return Number of changes done
* @throws IOException On error
* @since Last change: 2018.06.10
*/
public static int syncResourceBundle(Path propFileMaster,Path propFileClient) throws IOException
{
int changes = 0;
try
{  
  Properties masterprops = Prop.loadWrapper(propFileMaster);  // z.B messages in US
  Properties clientprops = Prop.loadWrapper(propFileClient);  // messages in DE

  String oldval,val,masterval;
  
  Set<String> masterkeys = masterprops.stringPropertyNames();  // liefert liste aller property namen z.B M_FILE_NOT_FOUND
  
  for (String k : masterkeys) 
  {   if (k.isEmpty()) continue;
  
      val = clientprops.getProperty(k); // gibt es den key auch in der neuen liste ?
      if (val == null) 
      {
          masterval = clientprops.getProperty(k,"");             
          clientprops.setProperty(k,masterval + "----TODO: TRANSLATE ME");        
          changes++;
      }
  } 
  //----- find obsolete keys in client
  
  Set<String> clientkeys = clientprops.stringPropertyNames(); 
  for (String k : clientkeys) 
  {   if (k.isEmpty()) continue;
  
      oldval = clientprops.getProperty(k);
  
      val = masterprops.getProperty(k); // gibt es den key auch in der master liste ?
      if (val == null) 
      {
          clientprops.remove(k);
          clientprops.setProperty("#_OBSOLETED_"+k,oldval);        
          changes++;
      }
  } 
  if (changes > 0) 
  { 
     Prop.storeWrapper(clientprops,propFileClient,"# Updated by syncResourceBundle() + " + MyTime.getCurrentTime());
  }
  return changes;
  
} catch (Exception e)
{
  throw new IOException("syncResourceBundle(): " + e.getMessage());
}   
} //-------------------------- end of syncResourceBundle()




/**
 * Reports the content of a properties container - sorted by key- to stdout.
 * @param props The Properties container
 * @param title A comment, may be null or empty
 * @since Last change: 2019.07.11 
 */
public static void print(Properties props,String title) 
{ 
try 
{   if (title != null && title.isEmpty()==false)  System.out.println(title);
    
    if (props == null || props.isEmpty()) System.out.println("--- The Properties container passed is null or empty");
    else
    {   Set<String> keys = props.stringPropertyNames();
        List<String> keylist = new ArrayList<String>();    
        keylist.addAll(keys);
        java.util.Collections.sort(keylist); 
        for (String k : keylist)  System.out.println("key='" + k + "' val='" + props.getProperty(k,"") + "'");    
        System.out.println("-----end");
        keylist.clear(); //keys.clear();
    }
} catch (Exception e) 
{  System.out.println("--- Prop.print() failed: " + e.toString()); }   

} //--------------------- end of print()

/**
 * Exports the properties container as textfile i.e. NOT in .properties format .
 * Pathnames use double backslashes.
 * @param props The Properties container, must not be null
 * @param textfile The output file, not in .properties format, must not be null
 * @param title A comment, may be null or empty
 * @param csvformat Option: Default is false and output format is: key=value, 
 * if true output format is: key;value;
 * @throws IOException On error
 * @since Last change: 2019.07.12 
 */
public static void report(Properties props,Path textfile,String title,boolean... csvformat) throws IOException
{ 
    final String fn="report()";

    boolean csv = false; if (csvformat.length > 0) csv = csvformat[0];
    final String EOL = "\r\n"; // System.getProperty("line.separator","\n");  
    String v;
BufferedWriter bw = null;
try
{
    bw = Files.newBufferedWriter(textfile,StandardCharsets.UTF_8); 
    
    if (props == null || props.isEmpty()) bw.write("# The Properties container passed is null or empty" + EOL);
    else
    {
        Set<String> keys = props.stringPropertyNames(); //// alle keys in keylist ablegen und sortieren
        List<String> keylist = new ArrayList<String>();    
        keylist.addAll(keys);
        java.util.Collections.sort(keylist); 
        StringBuffer sb = new StringBuffer();
        boolean changed=false; char c;
      
        for (String k : keylist)  
        {   
              v = props.getProperty(k,""); // replace dangerous control chars 
              //------------- fix the key
              changed = false; 
              sb.setLength(0);
              for (int i =0;i < k.length();i++)
              {
                 c = k.charAt(i);
                 switch(c)
                 {
    //             case '\\': sb.append("\\\\"); changed = true;
    //                        break;
                 case '=': sb.append("\\="); changed = true;
                            break;
                 case '\n': sb.append("\\n"); changed = true;
                            break;
                 case '\r': sb.append("\\r"); changed = true;
                            break;  
                 case '\f': sb.append("\\f"); changed = true;
                            break;     
                 case '\t': sb.append("\\t"); changed = true;
                            break; 
                 default:   sb.append(c);
                 }
              }
              if (changed) k = sb.toString();
              //------------- fix the value
              changed = false; 
              sb.setLength(0);
              for (int i =0;i < v.length();i++)
              {
                 c = v.charAt(i);
                 switch(c)
                 {
                 case '\\': sb.append("\\\\"); changed = true;
                            break;
                 case '\n': sb.append("\\n"); changed = true;
                            break;
                 case '\r': sb.append("\\r"); changed = true;
                            break;  
                 case '\f': sb.append("\\f"); changed = true;
                            break;     
                 case '\t': sb.append("\\t"); changed = true;
                            break; 
                 default:   sb.append(c);
                 }
              }
              if (changed) v = sb.toString();
              
              if (!csv) bw.write(k + "=" + v + EOL);     
              else      bw.write(k + ';' + v + EOL);    
        }
        keylist.clear(); //keys.clear();
    }
    bw.close();
    
} catch (Exception e)
{
    try { bw.close(); } catch (Exception ee) {} 
    throw new IOException(fn + ": " + e.getMessage() + " (file: " + textfile +")"); 
}          
} //--------------------- end of report()











/**
* Expands all Properties values in a Properties container. Three types are supported: <br>
* 1) System property names like user.name embedded in $[...]<br>
* 2) Environment variables like TEMP embedded in $%...% and<br>
* 3) Other properties in the container embedded in ${...}.
* Optionally - an expand-item may contain a default value (literal) behind a pipe | after the variable name
* - for eaxample tempdir = $%TEMP|C:\\temp%<br>
* If a variable does not exist and there is no default value, an IOException is thrown.
* Valid examples are:<br>
* welcome = Hello $[user.name|guest] Congratulation: The installation is finished.<br>
* tempdir = $%TEMP|C:\\temp%<br>
* foo = ${other_prop|default}\\bar<br>
* @param props The Properties container
* @return Number of properties expanded 
* @throws IOException If a property cannot be expanded - e.g. missing variable without a default value
* @since Last change: 2019.09.28
*/
public static int expandProperties(/*IO*/Properties props) throws IOException
{  
  if (props == null) return 0; //  throw new IllegalArgumentException("Arg. 'Properties props' (null) passed to expandProperties()");
  
  Set<String> keys = props.stringPropertyNames();
  int many=0;
  String newval,val,key=""; 
  try
  {
      for (String k : keys)
      {  
          if ( (val = props.getProperty(k)) == null) continue;   
          if (!val.contains("${") && !val.contains("$%") && !val.contains("$[")) continue;
          key = k;        
          if ( (newval = expandHelper(k,val,props)) != null)
          { 
               logger.log(Level.FINER,"The expanded value of \"{0}\" is \"{1}\"",new Object[] {k,newval} );
               props.setProperty(key,newval);  
               many++;
          } 
      } //------------------- for all keys   
  
  } catch (IOException e)
  { throw new IOException("expandProperties(): Cannot expand key '"+key+"': " + e.getMessage()); }
  
  return many;
} //--------------------- end of expandProperties()

/////////////////////////////////////////////////////////////////////////////////
// Now: PRIVATE methods 
/////////////////////////////////////////////////////////////////////////////////

//Invoked from loadProper()
private static String[] parseLineProper(String line) throws IOException
{   
 String[] keyAndValue = new String[] { "","" }; // the return value
 logger.log(Level.FINER,"Logical line is: \"{0}\"",line); 
 //======================================
 //--- STEP: 1) Parse the line to the first = to find the key
 //======================================   
 String key = "",tmpval=""; 
 char c;
 int len = line.length();
 int pos = 0;
 for (; pos < len;pos++) 
 {  
    c = line.charAt(pos); 
    if (c == '=')
    {   key = line.substring(0,pos).trim();
        tmpval = line.substring(pos+1);
        break;
    }
    if (Character.isISOControl(c))
       throw new IOException("Invalid key, Control character at pos " + pos + ": 0x" + Integer.toHexString((int)c) + ", key: '"+key+"'");
    if (c == '\\')
       throw new IOException("Invalid key, a backslash is not allowed, key: '"+key+"'");
    if (c < '\u0020' || c  > '\u007e') // isASCII
       throw new IOException("Invalid key, Character " + c + " not allowed (ASCII char expected), key: '"+key+"'");     
    if (c == '\\')
       throw new IOException("Invalid key, a backslash is not allowed, key: '"+key+"'");
 }   
 if (pos >= len)
   throw new IOException("Line contains no '=' key/value separator, line: '" + line + "'");  

 keyAndValue[0] = key;
 //logger.log(Level.FINER,"The validated key is \"{0}\"",key);
 
 //======================================
 //--- STEP: 2) Parse the value
 //======================================
 //----- STEP: 2.1) Skip white spaces in front of the val
 // both - key and/or value may be empty
 len = tmpval.length();
 c = 'x';
 for (pos=0;pos < len;pos++)
 {   c = tmpval.charAt(pos); 
     if (c != ' ' && c != '\t') break; 
 } 
 StringBuffer sbuf = new StringBuffer();
 char uc,prev = 'x'; int many; String codepointStr;

 //pos points to the first char in tmpval
 for ( ;pos < len;pos++) // now scan the value tmpval
 {   
     c = tmpval.charAt(pos); 
     switch(c)
     { case '\\':
           if (prev == '\\') 
           { sbuf.append(c); // an escaped backslash was found
             prev = 'x'; 
           } else prev = c;
           break;
       case 't':   // other escape sequences e.g backslash-b for backspace     
           if (prev == '\\') sbuf.append('\t');     else sbuf.append(c);   
           prev = c;
           break;
       case 'r':        
           if (prev == '\\') sbuf.append('\r');     else sbuf.append(c);   
           prev = c;
           break;
       case 'n':        
           if (prev == '\\') sbuf.append('\n');     else sbuf.append(c);   
           prev = c;
           break;
       case 'f':        // formfeed is an unvisible char, displayed aa a box 
           if (prev == '\\') sbuf.append('\f');     else sbuf.append(c);   
           prev = c;
           break;
       case 'u':
           if (prev == '\\') // a unicode escape sequence follows
           {                  
               pos++; // skip u
               many = Math.min(len-pos,4);
               codepointStr = tmpval.substring(pos,pos+many);     
               try
               {   uc = convertUnicode(codepointStr); // 12F4   
                   sbuf.append(uc);
                   pos += 3; // points to the last of the 4 digits                       
               } catch (Exception e)
               { throw new IOException(e.getMessage() + ", string: '" + tmpval + "' at key: '" +key + "'"); }
               
           } else 
              sbuf.append(c);
           prev = c;
           break;
       default: sbuf.append(c); prev = c;           
     } //---------------------------------------- end of switch  
 } //------------ parse the value  
 
 keyAndValue[1] = sbuf.toString();
 //logger.log(Level.FINER,"The final value is: \"{0}\"",keyAndValue[1]);
 return keyAndValue;   
} //----------------------------------------- end of parseLineProper()  


/**
* Expands a value 'val' of 'key'.
* @param key The nanme of the value, e.g. a property name
* @param val The value of 'key'
 * @param props The container to find ${ } properties
* @return The expanded value or null if nothing to expand was found in 'val'
 * @throws IOException If a referenced variable does not exist and and contains no default value
*/
private static String expandHelper(String key,String val,Properties props) throws IOException
{  
    final String expType[] =  new String[] {"System property", "Environment vaiable", "Property"};
    final String beginarr[] = new String[] {"$[",                  "$%",        "${"}; 
    final String endarr[] =   new String[] {"]",                   "%",         "}"};
    
    int[] idxFound = new int[1];
    String varname,toExpand,endStr,beginStr,def,expVar="";
    int kind, pos, pos_b, pos_e; 
    
    StringBuffer sbval = new StringBuffer(val);
    
    int start = 0, cnt=0;
    while ( (pos_b = indexOfAny(sbval.toString(), start,/*OUT*/idxFound, beginarr)) != -1)  
    {    
        kind = idxFound[0];   
        beginStr = beginarr[kind];
        endStr   = endarr[kind];
        
        if ( (pos_e = sbval.indexOf(endStr,pos_b + beginStr.length())) == -1)
        {   //throw new IOException("Missing closing string '" + endStr + "' of '" + beginStr + "', key: '" + key + "'");      
            logger.log(Level.WARNING,"Possibly syntax error at key \"{0}\", missing closing expand string \"{1}\" in \"{2}\"",
                    new Object[] {key,endStr,sbval} );  // es könnte ja $% ein nutzzeichen sein
            start = pos_b + beginStr.length();
            continue;
        }
        toExpand = sbval.substring(pos_b,pos_e + endStr.length()); // looks like: $%TEMP|C:\\temp%  or $%TEMP%    
        //logger.log(Level.FINER,"Variable to expand: {0}",toExpand);
        
        varname = sbval.substring(pos_b + beginStr.length(), pos_e); // e.g. LogDir or LogDir|C:\\temp
        
        if ( (pos = varname.indexOf('|')) == -1) def = null;
        else       
        {   
          def = varname.substring(pos+1);
          varname = varname.substring(0,pos);       
          if (containsAny(def,beginarr))
             throw new IOException("The default value must be a literal: '" + toExpand+"'");   
        }
        //logger.log(Level.FINER,"Variable to replace is \"{0}\",default is: \"{1}\"",new Object[] {varname,def});
        
        switch(kind)
        { case 0: // system property e.g. user.home
              expVar = System.getProperty(varname);   
              break;
          case 1: // environment
              expVar = System.getenv(varname);     
              break;
          default: // other property
              expVar = props.getProperty(varname);
        }  //------ end switch
       
        if (expVar == null)
        {
            if (def == null)
               throw new IOException("Missing referenced " + expType[kind] + " '" + varname + "' in value: '" + val + "'");  
            expVar = def;
            logger.log(Level.WARNING,"{0} \"{1}\" does not exist at key \"{2}\", using default \"{3}\"",
                    new Object[] {expType[kind],varname,key,def});
        }
        //logger.log(Level.FINER,"Variable \"{0}\" is: \"{1}\"",new Object[] {varname,expVar});
        
        sbval.replace(pos_b,pos_e + endStr.length(),expVar);
        
        start = pos_b; // DONT CHANGE !
        cnt++;
    } // ----------------- end of: looking 

    if (cnt == 0) return null; // nothing to expand
    return sbval.toString(); 
} //------------------------------- end of expandHelper()

/*
 * Determines if the line has a continuation line by examiing the last backslashes. For example:<br>
 * logdir = C:\\temp\\log\\  results in false<br> 
 * logdir = C:\\temp\\\  results in true<br>
 * @param line Line to be examined, must not be null
 * @return true if 'line' contains a continnuation line
 */
private static boolean hasContLine(String line)
{  
 if (line == null) return false;
 boolean hasCont = false;
 for (int i = line.length()-1;i >= 0;i--) //
 {
   if (line.charAt(i) != '\\') break;
   hasCont = !hasCont; // toggle
 }
 return hasCont;
} //--------------------------- end of hasContLine()

/**
 * Converts the 4 hex digits of an unicode escape sequence like \u263A to a unicode char. On error an
 * IllegalArgumentException is thrown, e.g. "Malformed \\uxxxx encoding: '263J', Char 'J' is not allowed        
 * @param codepointStr The 4 digits of the Unicode sequence (codepoint) e.g. "263A" or "236a", must not be null
 * @return The unicode character
 */
private static char convertUnicode(String codepointStr)
{
    if (codepointStr == null) throw new IllegalArgumentException("Arg. 'String codepointStr' = null was passed to convertUnicode()");
    
    if (codepointStr.length() < 4)
        throw new IllegalArgumentException("Malformed \\uxxxx encoding: '" + codepointStr + "', 4 hex digits expected");
           
    char c; final String valid = "0123456789ABCDEF";     
    int num,ucp=0/*the unicode code point*/,f = 1; // factor f is: 1 16, 256, 4096 
    
    for (int i=3;i >= 0;i--)
    {
        c = codepointStr.charAt(i);    c = Character.toUpperCase(c); 
        num = valid.indexOf(c);
        if (num != -1) ucp += num * f;
        else
           throw new IllegalArgumentException("Malformed \\uxxxx encoding: '" + codepointStr + "', Char '" + c + "' is not allowed");         
        
        f *= 16; 
    } //------------- prev char
//  BigInteger bi = new BigInteger(codepointStr,16);   return (char)bi.intValue();  
      
    char uc = (char)ucp; // casting the codepoint to the unicode char    
//    if (Character.isSurrogate(uc)) // Makes no sense: Check for surrogate char (0xD800 - 0xDFFF)
//      throw new IllegalArgumentException("Malformed \\uxxxx encoding: '" + codepointStr + "' is not allowed (Surrogate char)");         
    return uc; 
} //------------------------ end of convertUnicode()


/**
 * Detects a file's char encoding ('Charset')
 * If no charset can be determined, StandardCharsets.ISO_8859_1 is returned <br>
 * @param file The existing textfile to be read
 * @return The determined Charset or StandardCharsets.ISO_8859_1 as fall back
 * @throws IOException On error, e.g. file does not exist
 * @since Last change: 2019.09.26
 */
private static Charset getCharset(Path file) throws IOException
{ 
    Charset cs = StandardCharsets.UTF_8;  // UTF-8 file may or may not contains a BOM
    BufferedReader br = null;
    try
    {   br = Files.newBufferedReader(file,cs);   // Test, if file can be read as UTF-8              
        if (br.readLine() != null) return cs;   
           
    } catch (MalformedInputException e2) 
    {   //logger.log(Level.FINER,"File cannot be read with charset {0} : {1}",new Object[] { cs,file} );    
    
    } catch (Exception e)
    {  String msg;  if (e instanceof IOException) msg = e.getMessage(); else msg = e.toString();
       throw new IOException("getCharset(): " + msg);      
    } finally
    {   try { br.close(); } catch (Exception ee) {} }   
    
    cs = getBOMsCharset(file);  
    if (cs != null) return cs;          
    return StandardCharsets.ISO_8859_1;
} //--------------------------------------- end of getCharset()

/**
 * Returns the Charset as described by BOM or null if no BOM exists
 * @param file The existing file to be examined. 
 * @return The Charset or null if no BOM exists
 * @throws IOException On error, e.g. file does not exist
 * @since Last change: 2019.09.26
 */
private static Charset getBOMsCharset(Path file) throws IOException
{  
    BufferedInputStream ifs = null;    // byte wise  
    byte bytes[] = new byte[4]; 
    
    try  // STEP: 1) Open as BINARY file and read 4 bytes
    {   ifs = new BufferedInputStream(new FileInputStream(file.toString()));    
        if (ifs.read(bytes) < 4)
           return null; 
        
    } catch (Exception e)
    {  throw new IOException(e.getMessage()); //file does not exists
    } finally
    {   try { ifs.close(); } catch (Exception ee) {} }   
    
    byte i1 = bytes[0],i2 = bytes[1],i3 = bytes[2],i4 = bytes[3]; 
   
    //--- STEP: 2) Check the first byte if there is a BOM
    if (i1 == (byte)0xEF || i1 == (byte)0xFF || i1 == (byte)0xFE || i1 == (byte)0x00) // possibly a BOM exists
    {     
        if (i1 == (byte)0xEF && i2 == (byte)0xBB && i3 == (byte)0xBF) 
            return StandardCharsets.UTF_8;  // EF BB BF
        
        if (i1 == (byte)0xFF && i2 == (byte)0xFE) 
        {      
           if (i3 == (byte)0x00 && i4 == (byte)0x00) 
           {        
               try
               { return Charset.forName("UTF-32LE");  
               } catch (Exception e) //UnsupportedCharsetException e) // ist eine unchecked ex, wid gefangen z.B im catch block von RuntimeEx.
               { return null; } //throw new IOException("Unsupported Charset name \""+csname + "\" in BOM of: " + file); }         
           } 
           return StandardCharsets.UTF_16LE; // FF FE   
        }
        if (i1 == (byte)0xFE && i2 == (byte)0xFF)  
            return StandardCharsets.UTF_16BE; // FE FF
        if (i1 == (byte)0x00 && i2 == (byte)0x00 && i3 == (byte)0xFE && i4 == (byte)0xFF)
        {
            try
            { return Charset.forName("UTF-32BE");  
            } catch (Exception e)
            { return null; }      
        }
    } 
    return null; // no BOM
} //------------------------------ end of getBOMsCharset()

/**
 * Retrieves the first position of any string from array 'lookfor' which is not null or empty.
 * @param str String to be examined, must not be null
 * @param start Start position in 'str' e.g. 0
 * @param idxFound IN,OUT: Array allocated for at least 1 element to store index of lookfor - otherwise an
 * IllegalArgumentException is thrown.
 * @param lookfor Options: strings to look for, at least one is expected
 * @return First position or -1 if not any lookfor strings exists
 * @since Last change: 2019.09.23 
 */
private static int indexOfAny(String str,int start,int[] idxFound,String... lookfor)
{ 
    if (str == null) return -1; 
    if (lookfor == null || lookfor.length < 1) return -1;
    if (idxFound == null || idxFound.length < 1) 
        throw new IllegalArgumentException("indexOfAny(): Arg. 'int[] idxFound' is null or empty, at least size 1 expected"); 
    idxFound[0] = -1; 
    
    int pos,minpos = Integer.MAX_VALUE;   String look;
    
    for (int i=0; i < lookfor.length; i++)
    {
        look = lookfor[i];
        if (look == null || look.isEmpty()) continue;
        pos = str.indexOf(look,start);
        if (pos != -1 && pos < minpos) 
        {   minpos = pos;
            idxFound[0] = i;
        }
    }
    if (minpos == Integer.MAX_VALUE) return -1;
    return minpos;
} // ----------------------------------------end of indexOfAny()


/**
 * Examines a string whether it contains any of the given strings 'subStrings'.
 * Null or empty elements in 'subStrings' are ignored.
 * @param str The string to be examined,must not be null
 * @param subStrings Option: At leas one sub strings must be passed
 * @return true if any of the subString was found in 'str', else false
 * @throws IllegalArgumentException If 'str' is null or no 'subsStrings' are passed
 * @since Last change: 2019.09.05
 */
private static boolean containsAny(String str,String... subStrings)
{  
    if (str == null) return false;
    if (subStrings != null && subStrings.length < 1) return false;             
    for (String s : subStrings) 
    { 
        if (s == null || s.isEmpty()) continue; // IMPORTANT: to check this
        if (str.contains(s)) return true;   
    }
    return false; 
} //----------------------------- containsAny()





} //------------------------------------- end of class
