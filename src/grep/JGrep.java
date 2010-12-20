package grep;

import grep.Grep.GrepResult;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

// TODO save state to config file
public class JGrep extends JFrame implements ActionListener, ListSelectionListener {
	private static final long serialVersionUID = 9035033306521981994L;
	static{
		try {
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {
			// throw new RuntimeException(e);
			// do nothing
		}
	}
	
	private File grepPath;
	private HashMap<File,ArrayList<GrepResult>> result = null;
	
	private JFileChooser fileChooser;
	private JTextField fileField;
	private JButton browseButton;
	private JTextField patternField;
	private JTextField extensionsField;
	private JButton searchButton;
	private JList fileList;
	private FileListModel fileListModel;
	private JEditorPane resultPane;
	private JLabel resultsText;
	private JCheckBox recurseBox;
	private JCheckBox caseBox;
	private JCheckBox regexBox;
	private JProgressBar progressBar;

	public JGrep(){
		// looking in two different places to workaround known Java bug with Vista and 7
		// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6519127
		String path = System.getenv("USERPROFILE");
		if(path == null)
			path = System.getProperty("user.home");
		grepPath = new File(path);
		
		initComponents();
	}
	
	private void grep(){
		progressBar.setVisible(true);
		
		// construct data
		grepPath = new File(fileField.getText());
		String patternStr = patternField.getText();
		final Pattern pattern;
		String extsStr = extensionsField.getText().replaceAll("\\s+", "").replace("\\.", "");
		final String[] exts = extsStr.split(",");
		final boolean recurse = recurseBox.isSelected();
		boolean caseInsense = caseBox.isSelected();
		boolean regex = regexBox.isSelected();
		
		// error checking
		if(!grepPath.exists()){
			warning("The path "+grepPath.getAbsolutePath()+" does not exist.","Invalid Path");
			return;
		}
		if(patternStr.equals("")){
			warning("Please specify a string to grep for.","Invalid Pattern");
			return;
		}
		int patternFlags = Pattern.MULTILINE;
		if(caseInsense)
			patternFlags |= Pattern.CASE_INSENSITIVE;
		if(!regex)
			patternFlags |= Pattern.LITERAL;
		try{
			pattern = Pattern.compile(patternStr,patternFlags);
		} catch (PatternSyntaxException e){
			warning(e.getDescription(), "Invalid Pattern");
			return;
		}
		
		// grep
		// TODO is there some way to be able to stop this after it starts?
		new Thread(new Runnable() {
			@Override
			public void run() {
				result = Grep.grep(grepPath, pattern, exts, recurse);
				SwingUtilities.invokeLater(updateResults);
			}
		}).start();
	}
	
	private Runnable updateResults = new Runnable(){
		@Override
		public void run() {
			if(result == null)
				return;
			
			resultPane.setText("");
			int matchCount = 0;
			String absPat = grepPath.getAbsolutePath();
			fileListModel.setFiles(result.keySet(),absPat);
			for(Entry<File, ArrayList<GrepResult>> e : result.entrySet()){
				matchCount += e.getValue().size();
			}
			fileList.setSelectedIndex(0);
			resultsText.setText(matchCount+" match"+(matchCount == 1 ? "" : "es")+" in "+result.size()+" file"+(result.size() == 1 ? "" : "s"));
			progressBar.setVisible(false);
		}
	};

	protected String grepToHTML(File file, int context) {
		ArrayList<GrepResult> r = result.get(file);
		StringBuilder out = new StringBuilder();
		out.append(
				"<html><head><style>" +
				".title { font-size: 1.2em; }" +
				".match { padding-top: 5px; }" +
				".text { font-family: monospace; }" +
				"</style>" +
				"</head><body>"
				);
		out.append("<div class=\"title\">"+file.getAbsolutePath()+"</div>");
		out.append("<div class=\"subtitle\">"+r.size()+" matches in file.</div>");
		for(GrepResult g : r){
			out.append(String.format("<div class=\"match\"><em>Match on line %d:</em><br /><div class=\"text\">",g.getLineNumber()));
			List<String> cont = g.getLinesBefore(context);
			for(String ln : cont)
				out.append(ln+"%s<br />");
			out.append("<strong>"+g.getLine()+"</strong>");
			cont = g.getLinesAfter(context);
			for(String ln : cont)
				out.append("<br />"+ln);
			out.append("</div></div>");
		}
		out.append("</body></html>");
		return out.toString();
	}

	private void initComponents() {
		fileChooser = new JFileChooser(grepPath);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		
		// create all panels
		JPanel body = new JPanel(new BorderLayout());
		JPanel nPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel sPanel = new JPanel(new BorderLayout());
		JPanel sPanelL = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel sPanelR = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JPanel content = new JPanel(new BorderLayout());
		
		sPanel.add(sPanelL,BorderLayout.CENTER);
		sPanel.add(sPanelR,BorderLayout.EAST);
		body.add(nPanel,BorderLayout.NORTH);
		body.add(sPanel,BorderLayout.SOUTH);
		body.add(content,BorderLayout.CENTER);
		
		add(body);
		
		// North panel
		nPanel.add(new JLabel("Path:"));
		fileField = new JTextField(grepPath.getAbsolutePath(),18);
		fileField.addActionListener(this);
		nPanel.add(fileField);
		
		browseButton = new JButton("Browse");
		browseButton.addActionListener(this);
		nPanel.add(browseButton);
		
		nPanel.add(new JLabel("Pattern:"));
		
		patternField = new JTextField(8);
		patternField.addActionListener(this);
		nPanel.add(patternField);
		
		nPanel.add(new JLabel("Extensions:"));
		
		extensionsField = new JTextField("*",5);
		nPanel.add(extensionsField);
		
		searchButton = new JButton("Grep");
		searchButton.addActionListener(this);
		nPanel.add(searchButton);
		
		// content
		fileListModel = new FileListModel();
		fileList = new JList(fileListModel);
		fileList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		fileList.addListSelectionListener(this);
		JScrollPane fileScroll = new JScrollPane(fileList);
		fileScroll.setPreferredSize(new Dimension(200,100));
		content.add(fileScroll,BorderLayout.WEST);
		
		resultPane = new JEditorPane("text/html","");
		JScrollPane resultScroll = new JScrollPane(resultPane);
		content.add(resultScroll,BorderLayout.CENTER);
		
		// south panel
		resultsText = new JLabel(" ");
		sPanelL.add(resultsText);
		
		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressBar.setPreferredSize(new Dimension(60,18));
		progressBar.setVisible(false);
		sPanelR.add(progressBar);
		
		sPanelR.add(new JLabel("Recurse Directories:"));
		
		recurseBox = new JCheckBox();
		sPanelR.add(recurseBox);
		
		sPanelR.add(new JLabel("Case Insensitive:"));
		
		caseBox = new JCheckBox();
		sPanelR.add(caseBox);
		
		sPanelR.add(new JLabel("Use RegEx:"));
		
		regexBox = new JCheckBox();
		sPanelR.add(regexBox);
		
		// final setup
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(700,600);
		setLocationRelativeTo(null);
		setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		Object src = evt.getSource();
		if(src == fileField){
			File f = new File(fileField.getText());
			if(f.exists()){
				if(!f.isDirectory())
					f = f.getParentFile();
				fileChooser.setCurrentDirectory(f);
			} else {
            	warning("The selected file: "+f.getAbsolutePath()+" does not exist.",
            			"File Not Found");
            }
		} else if(src == browseButton){
			int returnVal = fileChooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File f = fileChooser.getSelectedFile();
	            if(f.exists())
	            	fileField.setText(f.getAbsolutePath());
	            else {
	            	warning("The selected file: "+f.getAbsolutePath()+" does not exist.",
	            			"File Not Found");
	            }
	        }
		} else if(src == searchButton || src == patternField){
			grep();
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent evt) {
		if (evt.getValueIsAdjusting() == false) {
	        if (fileList.getSelectedIndex() == -1) {
		        // nothing selected, nothing to change
	        } else {
	        	File f = fileListModel.getFileAt(fileList.getSelectedIndex());
	        	resultPane.setText(grepToHTML(f,0));
	        }
	    }
	}
	
	private void warning(String title, String message){
		JOptionPane.showMessageDialog(this, message, title,JOptionPane.WARNING_MESSAGE);
	}
	
	private class FileListModel extends AbstractListModel {
		private static final long serialVersionUID = -7045093302929107928L;
		private ArrayList<File> files = new ArrayList<File>();
		private String rootPath = "";

		public void setFiles(Set<File> f, String rp) {
			fireIntervalRemoved(this, 0, files.size());
			files = new ArrayList<File>(f);
			rootPath = rp;
			Collections.sort(files);
			fireIntervalAdded(this, 0, files.size());
		}

		@Override
		public Object getElementAt(int row) {
			return files.get(row).getAbsolutePath().substring(rootPath.length());
		}
		
		public File getFileAt(int row){
			return files.get(row);
		}

		@Override
		public int getSize() {
			return files.size();
		}
		
	}
	
	public static void main(String[] args){
		new JGrep();
	}
}
