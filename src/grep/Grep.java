/*
 * Copyright 2010 Michael Diamond - http://www.DigitalGemstones.com
 * 
 * This file is part of jGrep.
 * 
 *  jGrep is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  jGrep is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with jGrep.  If not, see <http://www.gnu.org/licenses/>.
 */
package grep;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Grep-style in-file find utility.  Output of main method does not
 * currently replicate Grep's output format.  Although this class is
 * intended to replicate Grep's functionality for use in Java programs,
 * it is not intended to be a replacement for grep on the command line.
 * 
 * @version 1.0.0
 * @author Michael Diamond
 */
// TODO test utility:
// Empty file
// file less than MAX_LINES
// match before MAX_LINES
// match in last MAX_LINES
// Recursive grep
// TODO grep a set of files
// TODO additional filters, age, size, etc.
public class Grep {
    public static void main(String[] argus){
        // Very limited CLI parsing, more powerful: http://jopt-simple.sourceforge.net/
        HashSet<String> options = new HashSet<>();
        ArrayList<String> args = new ArrayList<>();

        // process options
        for(int i = 0; i < argus.length; i++){
            if(argus[i].charAt(0) == '-'){
                options.add(argus[i].replaceFirst("--?", ""));
            } else {
                args.add(argus[i]);
            }
        }
        
        if(args.size() < 2){
            System.out.println("Usage: Grep [-r|--recurse] PATTERN PATH [extensions]");
            return;
        }
        
        if(args.size() == 2){
            args.add("*");
        }

        System.out.println(toText(
                grep(new File(args.get(1)),Pattern.compile(args.get(0)),args.get(2).split(","),options.contains("r") || options.contains("recurse"))));
    }
    
    public static final int MAX_LINES = 10;
    private static volatile boolean stop = false;
    
    public static String toText(HashMap<File,ArrayList<GrepResult>> res){
        return toText(res,0);
    }
    
    public static String toText(HashMap<File,ArrayList<GrepResult>> res, int context){
        // TODO should output relative to search location
        String out = "";
        for(Entry<File,ArrayList<GrepResult>> e : res.entrySet()){
            out += e.getKey().getName()+"\n";
            for(GrepResult gr : e.getValue()){
                out += " Match on Line "+gr.getLineNumber()+":\n";
                List<String> bef = gr.getLinesBefore(context);
                for(String ln : bef)
                    out += "   "+ln+"\n";
                out += " * "+gr.getLine()+"\n";
                List<String> aft = gr.getLinesBefore(context);
                for(String ln : aft)
                    out += "   "+ln+"\n";
            }
        }
        return out;
    }
    
    public static HashMap<File,ArrayList<GrepResult>> grep(File file, Pattern pattern, FileFilter ff, boolean recursive){
        HashMap<File,ArrayList<GrepResult>> res = new HashMap<>();
        if(!file.isDirectory()){
            ArrayList<GrepResult> ret = grepFile(file,pattern);
            if(ret.size() > 0)
                res.put(file,ret);
            return res;
        }
        File[] list = file.listFiles(ff);
        if(list == null) // just in case there's an IO error
            return res;
        for(File f : list){
            if(f.isDirectory()){
                if(recursive)
                    res.putAll(grep(f,pattern,ff,recursive));
            } else {
                ArrayList<GrepResult> ret = grepFile(f,pattern);
                if(ret.size() > 0)
                    res.put(f,ret);
            }
        }
        return res;
    }
    
    public static HashMap<File,ArrayList<GrepResult>> grep(File file, String pattern, FileFilter ff, boolean recursive){
        return grep(file,Pattern.compile(pattern),ff,recursive);
    }
    
    public static HashMap<File,ArrayList<GrepResult>> grep(File file, Pattern pattern, String[] extensions, boolean recursive){
        FileFilter ff = new ExtensionFilter(extensions);
        return grep(file,pattern,ff,recursive);
    }
    
    public static ArrayList<GrepResult> grepFile(File file, Pattern pattern){
        ArrayList<GrepResult> res = new ArrayList<>();
        try (Scanner in = new Scanner(file)) {
            LinkedList<String> curLines = new LinkedList<>();
            int lineNum = 1;
            while(in.hasNextLine() && curLines.size() < MAX_LINES*2+1){
                curLines.add(in.nextLine());
            }
            for(int i = 0; i < MAX_LINES && i < curLines.size(); i++){ // invariant: the lines less than MAX_LINES
                GrepResult gr = makeMatch(i,curLines,pattern,lineNum++);
                if(gr != null)
                    res.add(gr);
            }
            while(in.hasNextLine()){ // invariant: the lines between MAX_LINES+1 and END-(MAX_LINES-1)
                GrepResult gr = makeMatch(MAX_LINES,curLines,pattern,lineNum++);
                if(gr != null)
                    res.add(gr);
                curLines.remove();
                curLines.add(in.nextLine());
            }
            for(int i = 0; i <= MAX_LINES && curLines.size() > MAX_LINES; i++){ // invariant: the lines after END-(MAX_LINES-1)
                GrepResult gr = makeMatch(MAX_LINES,curLines,pattern,lineNum++);
                if(gr != null)
                    res.add(gr);
                curLines.remove();
            }
            in.close();
        } catch (FileNotFoundException e) {
            // TODO Improve error reporting
            System.err.println(e);
        }
        return res;
    }
    
    private static GrepResult makeMatch(int i, LinkedList<String> lines, Pattern pattern, int lineNum){
        if(stop){
            throw new GrepStopException("Grep is stopped.  Grep must be manually restarted to use.");
        }
        String ln = lines.get(i);
        Matcher m = pattern.matcher(ln);
        if(m.find()){
            return new GrepResult(lineNum,ln,m,lines.subList(0, i),lines.subList(i+1, lines.size()));
        }
        return null;
    }
    
    public static void replace(HashMap<File,ArrayList<GrepResult>> result, String replace) throws IOException{
        if(result == null)
            return;
        ArrayList<File> origs = new ArrayList<>();
        for(Entry<File,ArrayList<GrepResult>> e : result.entrySet()){
            File f = e.getKey();
            ArrayList<GrepResult> al = e.getValue();
    
            String forigStr = f.getAbsolutePath()+".orig";
            File forig = new File(forigStr);
            for(int i = 1; forig.exists(); i++){
                forig = new File(forig.getAbsolutePath()+i);
            }
            if(!f.renameTo(forig))
                throw new IOException("Failed to rename File.");
            origs.add(forig);
            
            String eol = FileFormat.getEOL(forig);
            try (Scanner in = new Scanner(forig);
            FileWriter out = new FileWriter(f)) {
                int line = 0;
                int nextMatch = 0;
                while(in.hasNextLine()){
                    String inLine = in.nextLine();
                    line++;
                    if(!in.hasNextLine()) // if we're on the last line, we don't want to append /another/ line
                        eol = "";
                    if(nextMatch < al.size() && line == al.get(nextMatch).getLineNumber()){
                        out.write(al.get(nextMatch).getMatcher().replaceAll(replace)+eol);
                        nextMatch++;
                    } else {
                        out.write(inLine+eol);
                    }
                }
            }
        }
        
        for(File orig : origs){
            orig.delete();
        }
    }
    
    /**
     * Used to interrupt a Grep search.  Since Grep makes no promises of being
     * thread safe, it should not be running more than one search at a time, but
     * this method will stop all searches.  If a Grep search is running while this
     * method is set, a GrepStopException - which extends RuntimeException - will be
     * thrown, and should be handled inside the thread running the search.  Note that
     * once Grep is locked, it must be manually unlocked once it is safe to do so.
     * @param lock true to stop all searches, false to allow them again
     */
    // I'm not very happy with this interrupt functionality at all, but it seems best atm
    public static synchronized void setGrepLock(boolean lock){
        stop = lock;
    }
    
    public static class GrepResult {
        int lineNum;
        String line;
        Matcher matches;
        LinkedList<String> before;
        LinkedList<String> after;
        
        public GrepResult(int ln, String lin, Matcher mat, List<String> lb, List<String> af){
            lineNum = ln;
            line = lin;
            matches = mat;
            before = new LinkedList<>(lb);
            after = new LinkedList<>(af);
        }
        
        public int getLineNumber(){
            return lineNum;
        }
        
        public String getLine(){
            return line;
        }
        
        public Matcher getMatcher(){
            return matches;
        }
        
        public List<String> getLinesBefore(int count){
            if(count < 0)
                throw new GrepException("Invalid count, must be non-negative.");
            if(count >= before.size())
                return new LinkedList<>(before);
            return before.subList(before.size()-count, before.size());
        }
        
        public List<String> getLinesAfter(int count){
            if(count < 0)
                throw new GrepException("Invalid count, must be non-negative.");
            if(count >= after.size())
                return new LinkedList<>(after);
            return after.subList(0, count);
        }
        
        @Override
        public String toString(){
            return "Match on line "+lineNum+": "+line;
        }
    }
    
    public static class GrepException extends RuntimeException {
        private static final long serialVersionUID = 81746759263988424L;
        
        public GrepException(String err){
            super(err);
        }
        
        public GrepException(String err, Throwable thr){
            super(err,thr);
        }
    }
    
    public static class GrepStopException extends GrepException {
        private static final long serialVersionUID = 81746759263988424L;
        
        public GrepStopException(String err){
            super(err);
        }
    }
    
    private static class ExtensionFilter implements FileFilter {
        String[] exts;
        boolean all = false;
        public ExtensionFilter(String[] exs){
            exts = new String[exs.length];
            for(int i = 0; i < exs.length; i++){
                exs[i] = exs[i].replaceAll("\\.", "").trim();
                if(exs[i].equals("*")){
                    all = true;
                    return;
                }
                exts[i] = "."+exs[i];
            }
        }
        @Override
        public boolean accept(File file) {
            if(all || file.isDirectory())
                return true;
            for(String e : exts){
                if(file.getAbsolutePath().endsWith(e))
                    return true;
            }
            return false;
        }
    }
    
    private static class FileFormat {
        public enum FileType { WINDOWS, UNIX, MAC, UNKNOWN }

        private static final char CR = '\r';
        private static final char LF = '\n';
        
        public static String getEOL(File f) throws IOException {
            switch (discover(f)){
            case WINDOWS:
                return "\r\n";
            case MAC:
                return "\r";
            case UNIX:
            case UNKNOWN: // treat as unix
            default:
                return "\n";
            }
        }

        public static FileType discover(File f) throws IOException {    

            try (Reader reader = new BufferedReader(new FileReader(f))) {
                return discover(reader);
            }
        }

        private static FileType discover(Reader reader) throws IOException {
            int c;
            while ((c = reader.read()) != -1) {
                switch(c) {        
                case LF: return FileType.UNIX;
                case CR: {
                    if (reader.read() == LF) return FileType.WINDOWS;
                    return FileType.MAC;
                }
                default: continue;
                }
            }
            return FileType.UNKNOWN;
        }
    }
}
