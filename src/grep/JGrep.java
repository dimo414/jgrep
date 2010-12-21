package grep;

import grep.Grep.GrepResult;
import grep.Grep.GrepStopException;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
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
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class JGrep extends JFrame implements ActionListener, ListSelectionListener, ChangeListener {
	private static final long serialVersionUID = 9035033306521981994L;
	static{
		try {
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {
			// throw new RuntimeException(e);
			// do nothing
		}
	}
	
	private File stateFile = new File(".jGrep.conf");
	private File grepPath;
	private Properties props;
	private HashMap<File,ArrayList<GrepResult>> result = null;

	private JMenuItem openMItem;
	private JMenuItem saveMItem;
	private JMenuItem replaceMItem;
	private JMenuItem helpMItem;
	private JFileChooser fileChooser;
	private JFileChooser saveChooser;
	private JTextField fileField;
	private JButton browseButton;
	private JTextField patternField;
	private JTextField extensionsField;
	private JButton searchButton;
	private JButton stopButton;
	private JList fileList;
	private FileListModel fileListModel;
	private JEditorPane resultPane;
	private JLabel resultsText;
	private JCheckBox recurseBox;
	private JCheckBox caseBox;
	private JCheckBox regexBox;
	private JProgressBar progressBar;
	private JSpinner contextSpinner;

	public JGrep(){
		// looking in two different places to workaround known Java bug with Vista and 7
		// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6519127
		String path = System.getenv("USERPROFILE");
		if(path == null)
			path = System.getProperty("user.home");
		grepPath = new File(path);
		
		initComponents();
		loadProperties(stateFile);
	}
	
	private boolean loadProperties(File f) {
		try {
			props = new Properties();
			FileInputStream in = new FileInputStream(f);
			props.load(in);
			in.close();
			fileField.setText(props.getProperty("path"));
			patternField.setText(props.getProperty("pattern"));
			extensionsField.setText(props.getProperty("extensions"));
			contextSpinner.setValue(Integer.parseInt(props.getProperty("context")));
			recurseBox.setSelected(props.getProperty("recurse").equals("true"));
			caseBox.setSelected(props.getProperty("case").equals("true"));
			regexBox.setSelected(props.getProperty("regex").equals("true"));
			
			File path = new File(fileField.getText());
			if(path.exists()){
				if(!path.isDirectory())
					path = path.getParentFile();
				fileChooser.setCurrentDirectory(path);
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	// data that is saved: path, pattern, extensions, context, recurse, case, regex
	private boolean saveProperties(File f, String desc){
		FileOutputStream out;
		try {
			props.setProperty("path", fileField.getText());
			props.setProperty("pattern", patternField.getText());
			props.setProperty("extensions", extensionsField.getText());
			props.setProperty("context", contextSpinner.getValue().toString());
			props.setProperty("recurse", recurseBox.isSelected()+"");
			props.setProperty("case", caseBox.isSelected()+"");
			props.setProperty("regex", regexBox.isSelected()+"");
			out = new FileOutputStream(f);
			props.store(out, "JGrep Configuration File: "+desc);
			out.close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void grep(){
		searchButton.setVisible(false);
		stopButton.setVisible(true);
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

		try {
			// error checking
			if(!grepPath.exists()){
				warning("Invalid Path", "The path "+grepPath.getAbsolutePath()+" does not exist.");
				throw new Exception();
			}
			if(patternStr.equals("")){
				warning("Invalid Pattern", "Please specify a string to grep for.");
				throw new Exception();
			}
			int patternFlags = Pattern.MULTILINE;
			if(caseInsense)
				patternFlags |= Pattern.CASE_INSENSITIVE;
			if(!regex)
				patternFlags |= Pattern.LITERAL;
			try{
				pattern = Pattern.compile(patternStr,patternFlags);
			} catch (PatternSyntaxException e){
				warning("Invalid Pattern", "Pattern has the following error: "+e.getDescription());
				throw new Exception();
		}
		} catch (Exception e){
			// restore to no-search state
			searchButton.setVisible(true);
			stopButton.setVisible(false);
			progressBar.setVisible(false);
			return;
		}
		
		// grep
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
				result = Grep.grep(grepPath, pattern, exts, recurse);
				} catch (GrepStopException e){
					Grep.setGrepLock(false);
					result = null;
				}
				SwingUtilities.invokeLater(updateResults);
			}
		}).start();
	}
	
	private Runnable updateResults = new Runnable(){
		@Override
		public void run() {
			if(result != null){
				resultPane.setText("");
				int matchCount = 0;
				String absPat = grepPath.getAbsolutePath();
				fileListModel.setFiles(result.keySet(),absPat);
				for(Entry<File, ArrayList<GrepResult>> e : result.entrySet()){
					matchCount += e.getValue().size();
				}
				fileList.setSelectedIndex(0);resultsText.setText(matchCount+" match"+(matchCount == 1 ? "" : "es")+" in "+result.size()+" file"+(result.size() == 1 ? "" : "s"));
			}
			searchButton.setVisible(true);
			stopButton.setVisible(false);
			progressBar.setVisible(false);
		}
	};

	protected String grepToHTML(File file, int context) {
		if(result == null){
			return "";
		}
		ArrayList<GrepResult> r = result.get(file);
		StringBuilder out = new StringBuilder();
		out.append(
				"<html><head><style>" +
				".title { font-size: 1.2em; }" +
				".matchBlock { padding-top: 5px; }" +
				".text { font-family: monospace; }" +
				".match { color: #ff0000; }" +
				"</style>" +
				"</head><body>"
				);
		out.append("<div class=\"title\">"+file.getAbsolutePath()+"</div>");
		out.append("<div class=\"subtitle\">"+r.size()+" matches in file.</div>");
		for(GrepResult g : r){
			out.append("<div class=\"matchBlock\"><em>Match on line "+g.getLineNumber()+"</em><br /><div class=\"text\">");
			List<String> cont = g.getLinesBefore(context);
			for(String ln : cont)
				out.append(htmlEscape(ln)+"<br />");
			out.append("<strong>"+highlightHtmlEscape(g.getLine(),g.getMatcher(),"span class=\"match\"")+"</strong>");
			cont = g.getLinesAfter(context);
			for(String ln : cont)
				out.append("<br />"+htmlEscape(ln));
			out.append("</div></div>");
		}
		out.append("</body></html>");
		return out.toString();
	}

	private void initComponents() {
		// Window Close Operation
 		addWindowListener(new WindowAdapter() {
 			@Override
 			public void windowClosing(java.awt.event.WindowEvent e) {
 				setVisible(false);
 				dispose();
 				
 				saveProperties(stateFile,"State on close");
 				
 				System.exit(0);
 			}
 		});
		
 		// file choosers
		fileChooser = new JFileChooser(grepPath);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		saveChooser = new JFileChooser(grepPath);
		
		// menu bar
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		
		JMenu optMenu = new JMenu("Options");
		menuBar.add(optMenu);
		
		openMItem = new JMenuItem("Open Search State");
		openMItem.addActionListener(this);
		optMenu.add(openMItem);
		
		saveMItem = new JMenuItem("Save Search State");
		saveMItem.addActionListener(this);
		optMenu.add(saveMItem);
		
		optMenu.add(new JSeparator());
		
		replaceMItem = new JMenuItem("Replace Matches");
		replaceMItem.addActionListener(this);
		optMenu.add(replaceMItem);
		
		optMenu.add(new JSeparator());
		
		helpMItem = new JMenuItem("Pattern Help");
		helpMItem.addActionListener(this);
		optMenu.add(helpMItem);
		
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
		
		nPanel.add(new TSeparator(SwingConstants.VERTICAL));
		
		// TODO some easy way to show basic regex operators
		nPanel.add(new JLabel("Pattern:"));
		
		patternField = new JTextField(8);
		patternField.addActionListener(this);
		nPanel.add(patternField);
		
		nPanel.add(new TSeparator(SwingConstants.VERTICAL));
		
		nPanel.add(new JLabel("Extensions:"));
		
		extensionsField = new JTextField("*",5);
		nPanel.add(extensionsField);
		
		nPanel.add(new TSeparator(SwingConstants.VERTICAL));
		
		searchButton = new JButton("Grep");
		searchButton.addActionListener(this);
		nPanel.add(searchButton);
		
		stopButton = new JButton("Stop");
		stopButton.addActionListener(this);
		stopButton.setVisible(false);
		nPanel.add(stopButton);
		
		// content
		fileListModel = new FileListModel();
		// TODO convert to table to show more data, like number of matches
		fileList = new JList(fileListModel);
		fileList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		fileList.addListSelectionListener(this);
		JScrollPane fileScroll = new JScrollPane(fileList);
		fileScroll.setPreferredSize(new Dimension(200,100));
		content.add(fileScroll,BorderLayout.WEST);
		
		resultPane = new JEditorPane("text/html","");
		JScrollPane resultScroll = new JScrollPane(resultPane);
		content.add(resultScroll,BorderLayout.CENTER);
		
		// south panel left
		resultsText = new JLabel(" ");
		sPanelL.add(resultsText);
		
		// south panel right
		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressBar.setPreferredSize(new Dimension(60,18));
		progressBar.setVisible(false);
		sPanelR.add(progressBar);
		
		sPanelR.add(new JLabel("Context Lines:"));
		
		contextSpinner = new JSpinner(new SpinnerNumberModel(0,0,Grep.MAX_LINES,1));
		((JSpinner.DefaultEditor)contextSpinner.getEditor()).getTextField().setEditable(false);
		contextSpinner.addChangeListener(this);
		sPanelR.add(contextSpinner);
		
		sPanelR.add(new TSeparator(SwingConstants.VERTICAL));
		
		sPanelR.add(new JLabel("Recurse Directories:"));
		
		recurseBox = new JCheckBox();
		sPanelR.add(recurseBox);
		
		sPanelR.add(new TSeparator(SwingConstants.VERTICAL));
		
		sPanelR.add(new JLabel("Case Insensitive:"));
		
		caseBox = new JCheckBox();
		sPanelR.add(caseBox);
		
		sPanelR.add(new TSeparator(SwingConstants.VERTICAL));
		
		sPanelR.add(new JLabel("Use RegEx:"));
		
		regexBox = new JCheckBox();
		sPanelR.add(regexBox);
		
		// final setup
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(750,600);
		setLocationRelativeTo(null);
		setVisible(true);
	}
	
	//
	// LISTENERS
	//

	@Override
	public void actionPerformed(ActionEvent evt) {
		Object src = evt.getSource();
		// menu events
		if(src == openMItem){
			int returnVal = saveChooser.showOpenDialog(this);
	        if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File file = saveChooser.getSelectedFile();
	            loadProperties(file);
	        }
		} else if(src == saveMItem){
			int returnVal = saveChooser.showSaveDialog(this);
	        if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File file = saveChooser.getSelectedFile();
	            saveProperties(file,"User-Saved Grep State");
	        }
		} else if(src == replaceMItem){
			// TODO replace functionality
		} else if(src == helpMItem){
			// TODO show info on regex usage
		} 
		
		// file selection
		else if(src == fileField){
			File f = new File(fileField.getText());
			if(f.exists()){
				if(!f.isDirectory())
					f = f.getParentFile();
				fileChooser.setCurrentDirectory(f);
			} else {
            	warning("File Not Found","The selected file: "+f.getAbsolutePath()+" does not exist.");
            }
		} else if(src == browseButton){
			int returnVal = fileChooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File f = fileChooser.getSelectedFile();
	            if(f.exists())
	            	fileField.setText(f.getAbsolutePath());
	            else {
	            	warning("File Not Found","The selected file: "+f.getAbsolutePath()+" does not exist.");
	            }
	        }
		}
		
		// search/stop
		else if(src == searchButton || src == patternField){
			grep();
		} else if(src == stopButton){
			Grep.setGrepLock(true);
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent evt) {
		Object src = evt.getSource();
		if(src == fileList){
			if (evt.getValueIsAdjusting() == false) {
		        if (fileList.getSelectedIndex() == -1) {
			        // nothing selected, nothing to change
		        } else {
		        	buildResultPane();
		        }
		    }
		}
	}

	@Override
	public void stateChanged(ChangeEvent evt) {
		Object src = evt.getSource();
		if(src == contextSpinner){
			buildResultPane();
		}
	}
	
	//
	// PRIVATE UTILITIES
	//
	
	private void buildResultPane(){
    	File f = fileListModel.getFileAt(fileList.getSelectedIndex());
    	resultPane.setText(grepToHTML(f,(Integer)contextSpinner.getValue()));
    	resultPane.setSelectionStart(0);
    	resultPane.setSelectionEnd(0);
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
			if(row < 0)
				return null;
			return files.get(row);
		}

		@Override
		public int getSize() {
			return files.size();
		}
		
	}
	
	private static class TSeparator extends JSeparator {
		private static final long serialVersionUID = -8339080768468333094L;

		TSeparator(int orient){
			super(orient);
			setPreferredSize(new Dimension(2,20));
		}
	}
	
	private static HashMap<Character,String> htmlEscapeMap = new HashMap<Character,String>();
	static {
		htmlEscapeMap.put('"', "&quot;");
		htmlEscapeMap.put('&', "&amp;");
		htmlEscapeMap.put('<', "&lt;");
		htmlEscapeMap.put('>', "&gt;");
	}
	private static String htmlEscape(String in){
		StringBuilder out = new StringBuilder(in.length()*2);
		for(int i = 0; i < in.length(); i++){
			char c = in.charAt(i);
			String esc = htmlEscapeMap.get(c);
			if(esc != null){
				out.append(esc);
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}
	
	private static String highlightHtmlEscape(String text, Matcher m, String tag){
		m.reset();
		StringBuilder out = new StringBuilder();
		int nextChar = 0;
		int space = tag.indexOf(" ");
		String tagClose = space > -1 ? tag.substring(0,space) : tag;
		while(m.find()){
			out.append(htmlEscape(text.substring(nextChar,m.start())));
			out.append("<"+tag+">");
			out.append(htmlEscape(text.substring(m.start(),m.end())));
			out.append("</"+tagClose+">");
			nextChar = m.end();
		}
		out.append(text.substring(nextChar));
		return out.toString();
	}
	
	public static void main(String[] args){
		new JGrep();
	}
}
