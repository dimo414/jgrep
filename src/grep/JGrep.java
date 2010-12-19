package grep;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;

// TODO save state to config file
public class JGrep extends JFrame implements ActionListener {
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
	
	private JFileChooser fileChooser;
	private JTextField fileField;
	private JButton browseButton;
	private JTextField patternField;
	private JTextField extensionsField;
	private JButton searchButton;
	private JCheckBox recurseBox;
	private JLabel resultsText;
	private JTextArea fileArea;
	private JTextArea resultArea;
	private JCheckBox caseBox;
	private JCheckBox regexBox;

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
		// construct data
		File search = new File(fileField.getText());
		String patternStr = patternField.getText();
		Pattern pattern;
		String extsStr = extensionsField.getText().replaceAll("\\s+", "").replace("\\.", "");
		System.out.println(extsStr);
		String[] exts = extsStr.split(",");
		boolean recurse = recurseBox.isSelected();
		boolean caseSense = caseBox.isSelected();
		boolean regex = regexBox.isSelected();
		
		// error checking
		if(!search.exists()){
			warning("The path "+search.getAbsolutePath()+" does not exist.","Invalid Path");
			return;
		}
		if(patternStr.equals("")){
			warning("Please specify a string to grep for.","Invalid Pattern");
			return;
		}
		int patternFlags = Pattern.MULTILINE;
		if(!caseSense)
			patternFlags |= Pattern.CASE_INSENSITIVE;
		if(!regex)
			patternFlags |= Pattern.LITERAL;
		try{
			pattern = Pattern.compile(patternStr,patternFlags);
		} catch (PatternSyntaxException e){
			warning(e.getDescription(), "Invalid Pattern");
			return;
		}
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
		nPanel.add(patternField);
		
		nPanel.add(new JLabel("Extensions:"));
		
		extensionsField = new JTextField("*",5);
		nPanel.add(extensionsField);
		
		searchButton = new JButton("Grep");
		searchButton.addActionListener(this);
		nPanel.add(searchButton);
		
		// content
		fileArea = new JTextArea(5,20);
		JScrollPane fileScroll = new JScrollPane(fileArea);
		content.add(fileScroll,BorderLayout.WEST);

		resultArea = new JTextArea(5,30);
		JScrollPane resultScroll = new JScrollPane(resultArea);
		content.add(resultScroll,BorderLayout.CENTER);
		
		// south panel
		resultsText = new JLabel(" ");
		sPanelL.add(resultsText);
		
		sPanelR.add(new JLabel("Recurse Directories:"));
		
		recurseBox = new JCheckBox();
		sPanelR.add(recurseBox);
		
		sPanelR.add(new JLabel("Case Sensitive:"));
		
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
		} else if(src == searchButton){
			grep();
		}
	}
	
	private void warning(String title, String message){
		JOptionPane.showMessageDialog(this, message, title,JOptionPane.WARNING_MESSAGE);
	}
	
	public static void main(String[] args){
		new JGrep();
	}
}
