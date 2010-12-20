package grep;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
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
 * A Grep-style in-file find utility.
 * 
 * @author Michael Diamond
 */
// TODO test utility:
// Empty file
// file less than MAX_LINES
// match before MAX_LINES
// match in last MAX_LINES
// Recusive grep
// TODO find/replace functionality
// TODO grep a set of files
// TODO additional filters, age, size, etc.
public class Grep {
	public static void main(String[] argus){
		// Very limited CLI parsing, more powerful: http://jopt-simple.sourceforge.net/
		HashSet<String> options = new HashSet<String>();
		ArrayList<String> args = new ArrayList<String>();

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
		HashMap<File,ArrayList<GrepResult>> res = new HashMap<File,ArrayList<GrepResult>>();
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
		ArrayList<GrepResult> res = new ArrayList<GrepResult>();
		try {
			Scanner in = new Scanner(file);
			LinkedList<String> curLines = new LinkedList<String>();
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
		} catch (FileNotFoundException e) {
			// report somehow that the file was not found
		}
		return res;
	}
	
	private static GrepResult makeMatch(int i, LinkedList<String> lines, Pattern pattern, int lineNum){
		String ln = lines.get(i);
		Matcher m = pattern.matcher(ln);
		if(m.find()){
			return new GrepResult(lineNum,ln,m,lines.subList(0, i),lines.subList(i+1, lines.size()));
		}
		return null;
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
			before = new LinkedList<String>(lb);
			after = new LinkedList<String>(af);
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
				throw new GrepException("Invalid cound, must be non-negative.");
			if(count >= before.size())
				return new LinkedList<String>(before);
			return before.subList(before.size()-count, before.size());
		}
		
		public List<String> getLinesAfter(int count){
			if(count < 0)
				throw new GrepException("Invalid cound, must be non-negative.");
			if(count >= after.size())
				return new LinkedList<String>(after);
			return after.subList(0, count);
		}
		
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
	
	private static class ExtensionFilter implements FileFilter {
		String[] exts;
		boolean all = false;
		public ExtensionFilter(String[] exs){
			exts = new String[exs.length];
			for(int i = 0; i < exs.length; i++){
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
}
