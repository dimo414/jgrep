import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
	
	private File grepPath = new File(System.getProperty("user.home"));
	
	private JFileChooser fileChooser;
	private JTextField fileField;
	private JButton browseButton;
	private JTextField patternField;
	private JTextField extensionsField;
	private JButton searchButton;

	public JGrep(){
		initComponents();
	}

	private void initComponents() {
		fileChooser = new JFileChooser(grepPath);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		
		// create all panels
		JPanel body = new JPanel(new BorderLayout());
		JPanel nPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel sPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel content = new JPanel(new BorderLayout());
		
		body.add(nPanel,BorderLayout.NORTH);
		body.add(sPanel,BorderLayout.SOUTH);
		body.add(content,BorderLayout.CENTER);
		
		// North panel
		nPanel.add(new JLabel("Path: "));
		fileField = new JTextField(grepPath.getAbsolutePath(),18);
		fileField.addActionListener(this);
		nPanel.add(fileField);
		
		browseButton = new JButton("Browse");
		browseButton.addActionListener(this);
		nPanel.add(browseButton);
		
		nPanel.add(new JLabel("Pattern: "));
		
		patternField = new JTextField(8);
		nPanel.add(patternField);
		
		nPanel.add(new JLabel("Extensions: "));
		
		extensionsField = new JTextField("*",5);
		nPanel.add(extensionsField);
		
		searchButton = new JButton("Grep");
		searchButton.addActionListener(this);
		nPanel.add(searchButton);
		
		add(body);

		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(800,600);
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
            	JOptionPane.showMessageDialog(this,
            			"The selected file: "+f.getAbsolutePath()+" does not exist.",
            			"File Not Found",JOptionPane.WARNING_MESSAGE);
            }
		} else if(src == browseButton){
			int returnVal = fileChooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File f = fileChooser.getSelectedFile();
	            if(f.exists())
	            	fileField.setText(f.getAbsolutePath());
	            else {
	            	JOptionPane.showMessageDialog(this,
	            			"The selected file: "+f.getAbsolutePath()+" does not exist.",
	            			"File Not Found",JOptionPane.WARNING_MESSAGE);
	            }
	        }
		}
	}
	
	public static void main(String[] args){
		new JGrep();
	}
}
