import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * A Grep-style in-file find utility.
 * 
 * @author Michael Diamond
 */
public class Grep {
	public static HashMap<File,ArrayList<GrepResult>> grep(File file, String pattern, FilenameFilter ff, boolean recursive){
		HashMap<File,ArrayList<GrepResult>> res = new HashMap<File,ArrayList<GrepResult>>();
		if(!file.isDirectory()){
			res.put(file,grepFile(file,pattern));
			return res;
		}
		for(File f : file.listFiles(ff)){
			if(f.isDirectory()){
				if(recursive)
					res.putAll(grep(f,pattern,ff,recursive));
			} else {
				res.put(f,grepFile(f,pattern));
			}
		}
		return res;
	}
	
	public static HashMap<File,ArrayList<GrepResult>> grep(File file, String pattern, String[] extensions, boolean recursive){
		FilenameFilter ff = new ExtensionFilter(extensions);
		return grep(file,pattern,ff,recursive);
	}
	
	public static ArrayList<GrepResult> grepFile(File file, String pattern){
		return null;
	}
	
	public static class GrepResult {
		int lineNum;
		String line;
		int start, end;
		LinkedList<String> before;
		LinkedList<String> after;
		
		public GrepResult(int ln, String lin, int st, int en, List<String> lb, List<String> af){
			lineNum = ln;
			line = lin;
			start = st;
			end = en;
			before = new LinkedList<String>(lb);
			after = new LinkedList<String>(af);
		}
		
		public int getLineNumber(){
			return lineNum;
		}
		
		public String getLine(){
			return line;
		}
		
		public String getMatch(){
			return line.substring(start,end);
		}
		
		public int getMatchStart(){
			return start;
		}
		
		public int getMatchEnd(){
			return end;
		}
		
		public LinkedList<String> getLinesBefore(int count){
			if(count < 1)
				throw new GrepException("Invalid cound, must be positive.");
			if(count >= before.size())
				return new LinkedList<String>(before);
			return (LinkedList<String>)before.subList(before.size()-count, before.size());
		}
		
		public LinkedList<String> getLinesAfter(int count){
			if(count < 1)
				throw new GrepException("Invalid cound, must be positive.");
			if(count >= after.size())
				return new LinkedList<String>(after);
			return (LinkedList<String>)after.subList(0, count);
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
	
	private static class ExtensionFilter implements FilenameFilter {
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
		public boolean accept(File dir, String name) {
			if(all)
				return true;
			for(String e : exts){
				if(name.endsWith(e))
					return true;
			}
			return false;
		}
	}
}
