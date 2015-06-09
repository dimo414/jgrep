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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

/**
 * A graphical in-file text search utility, intended to provide much
 * of the usefulness of GNU Grep to people regularly using graphical tools
 * and not command line tools.  Users can do literal text searches and
 * use Java's regular expression engine to find text, as well as browse
 * the matches and surrounding context of the files being explored.
 * 
 * Additional functionality includes the ability to save past searches
 * and find/replace on all matches.  Potential future features include
 * saving output to a set of files and deeper customization of the files
 * to search through - currently only directories or individual files can
 * be specified.
 * 
 * This program is not intended to be a replacement for Grep, and as such
 * cannot replicate some of Grep's command line functionality like piping
 * input and output.
 * 
 * @version 1.0.1
 * @author Michael Diamond
 */
public class JGrep extends JFrame implements ActionListener, ListSelectionListener, ChangeListener {
    private static final long serialVersionUID = 9035033306521981994L;
    static{
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception e2){
                // throw new RuntimeException(e);
                // do nothing, stick with default look and feel
            }
        }
    }
    
    File stateFile = new File(".jGrep.conf");
    File grepPath;
    private Properties props;
    HashMap<File,ArrayList<GrepResult>> result = null;

    private JMenuItem openMItem;
    private JMenuItem saveMItem;
    private JMenuItem replaceMItem;
    private JMenuItem charHelpMItem;
    private JPanel charHelpPanel;
    private JMenuItem quantHelpMItem;
    private JMenuItem aboutMItem;
    private JPanel quantHelpPanel;
    private JFileChooser fileChooser;
    private JFileChooser saveChooser;
    private JTextField fileField;
    private JButton browseButton;
    private JTextField patternField;
    private JTextField extensionsField;
    JButton searchButton;
    JButton stopButton;
    JTable fileTable;
    FileListModel fileTableModel;
    JEditorPane resultPane;
    JLabel resultsText;
    private JCheckBox recurseBox;
    private JCheckBox caseBox;
    private JCheckBox regexBox;
    JProgressBar progressBar;
    private JSpinner contextSpinner;

    public JGrep(boolean localState){
        super("jGrep");
        grepPath = getUserDir();
        
        if(!localState){//this is the installed version of jGrep
            stateFile = new File(getUserDir(),".jGrep.conf");
        }
        initComponents();
        loadProperties(stateFile);
    }
    
    private static File getUserDir(){
        // looking in two different places to workaround known Java bug with Vista and 7
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6519127
        String path = System.getenv("USERPROFILE");
        if(path == null)
            path = System.getProperty("user.home");
         return new File(path);
    }
    
    private boolean loadProperties(File f) {
        props = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            props.load(in);
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return false;
        }
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
    }
    
    // data that is saved: path, pattern, extensions, context, recurse, case, regex
    boolean saveProperties(File f, String desc){
        props.setProperty("path", fileField.getText());
        props.setProperty("pattern", patternField.getText());
        props.setProperty("extensions", extensionsField.getText());
        props.setProperty("context", contextSpinner.getValue().toString());
        props.setProperty("recurse", recurseBox.isSelected()+"");
        props.setProperty("case", caseBox.isSelected()+"");
        props.setProperty("regex", regexBox.isSelected()+"");
        try (FileOutputStream out = new FileOutputStream(f)) {
            props.store(out, "JGrep Configuration File: "+desc);
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
    
    Runnable updateResults = new Runnable(){
        @Override
        public void run() {
            if(result != null){
                resultPane.setText("");
                String absPat = grepPath.getAbsolutePath();
                fileTableModel.setFiles(result.keySet(),absPat);
                if(result.size() == 0){
                    resultsText.setText("No matches found");
                } else {
                    int matchCount = 0;
                    for(ArrayList<GrepResult> al : result.values()){
                        matchCount += al.size();
                    }
                    fileTable.changeSelection(0, 0, false, false);
                    resultsText.setText(matchCount+" match"+(matchCount == 1 ? "" : "es")+" in "+result.size()+" file"+(result.size() == 1 ? "" : "s"));
                }
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
                "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">" +
                "<html><head><style type=\"text/css\">" +
                "body { font-family: sans-serif; }" +
                ".title { font-size: 1.2em; }" +
                ".matchBlock { padding-top: 5px; }" +
                ".text { font-family: monospace; }" +
                ".match { color: #ff0000; }" +
                "</style>" +
                "<title>"+file.getAbsolutePath()+"</title>" +
                "</head><body>"
                );
        out.append("<div class=\"title\">"+file.getAbsolutePath()+"</div>");
        out.append("<div class=\"subtitle\">"+r.size()+" matches in file.</div>");
        for(GrepResult g : r){
            out.append("<div class=\"matchBlock\"><em>Match on line "+g.getLineNumber()+"</em><br><div class=\"text\">");
            List<String> cont = g.getLinesBefore(context);
            for(String ln : cont)
                out.append(htmlEscape(ln)+"<br>");
            out.append("<strong>"+highlightHtmlEscape(g.getLine(),g.getMatcher(),"span class=\"match\"")+"</strong>");
            cont = g.getLinesAfter(context);
            for(String ln : cont)
                out.append("<br>"+htmlEscape(ln));
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
        
        JMenu helpMenu = new JMenu("Pattern Help");
        optMenu.add(helpMenu);
        
        aboutMItem = new JMenuItem("About jGrep");
        aboutMItem.addActionListener(this);
        optMenu.add(aboutMItem);
        
        charHelpMItem = new JMenuItem("Characters and Character Classes");
        charHelpMItem.addActionListener(this);
        helpMenu.add(charHelpMItem);
        
        quantHelpMItem = new JMenuItem("Special Quantifier Characters");
        quantHelpMItem.addActionListener(this);
        helpMenu.add(quantHelpMItem);
        
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
        fileTableModel = new FileListModel();
        fileTable = new JTable(fileTableModel);
        fileTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        fileTable.getSelectionModel().addListSelectionListener(this);
        fileTable.setAutoCreateRowSorter(true);
        JScrollPane fileScroll = new JScrollPane(fileTable);
        fileScroll.setPreferredSize(new Dimension(230,100));
        content.add(fileScroll,BorderLayout.WEST);
        
        resultPane = new JEditorPane("text/html","");
        resultPane.setEditable(false);
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
        recurseBox.setSelected(true);
        sPanelR.add(recurseBox);
        
        sPanelR.add(new TSeparator(SwingConstants.VERTICAL));
        
        sPanelR.add(new JLabel("Case Insensitive:"));
        
        caseBox = new JCheckBox();
        caseBox.setSelected(true);
        sPanelR.add(caseBox);
        
        sPanelR.add(new TSeparator(SwingConstants.VERTICAL));
        
        sPanelR.add(new JLabel("Use RegEx:"));
            
        regexBox = new JCheckBox();
        sPanelR.add(regexBox);
        
        // final setup
        setIconImage(new ImageIcon(getClass().getClassLoader().getResource("jGrep Logo 64.png")).getImage());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(800,600);
        setLocationRelativeTo(null);
        setVisible(true);
        
        // help panels
        charHelpPanel = new JPanel(new BorderLayout());
        charHelpPanel.add(new JLabel("<html><h4>Java Regular Expressions Character Reference</h4></html>"),BorderLayout.NORTH);
        charHelpPanel.add(new JLabel("<html><table>" +
                "<tr><td><strong><u>Char</u></strong></td><td><strong><u>Matches</u></strong></td></tr>" +
                "<tr><td><em>x</em></td><td>The literal character '<em>x</em>'</td></tr>" +
                "<tr><td>.</td><td>Matches any character</td></tr>" +
                "<tr><td>\\x<em>hh</em></td><td>The character with hex code <em>hh</em></td></tr>" +
                "<tr><td>\\u<em>hhhh</em></td><td>The character with hex code <em>hhhh</em></td></tr>" +
                "<tr><td>\\t</td><td>The tab character</td></tr>" +
                "<tr><td>\\d</td><td>Any digit - [0-9]</td></tr>" +
                "<tr><td>\\D</td><td>Any non-digit - [^0-9]</td></tr>" +
                "<tr><td>\\s</td><td>Any whitespace character - [ \\t\\n\\x0B\\f\\r]</td></tr>" +
                "<tr><td>\\S</td><td>Any non-whitespace character - [^\\s]</td></tr>" +
                "<tr><td>\\w</td><td>Any word character - [a-zA-Z_0-9]</td></tr>" +
                "<tr><td>\\W</td><td>Any non-word character - [^\\w]</td></tr>" +
                "<tr><td>\\\\</td><td>The backslash character - single backslash escapes special chars</td></tr>" +
                "</table><br></html>"),BorderLayout.CENTER);
        charHelpPanel.add(new SwingLink("Full Java RegEx Documentation",
                "http://download.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html#sum"),BorderLayout.SOUTH);
        
        quantHelpPanel = new JPanel(new BorderLayout());
        quantHelpPanel.add(new JLabel("<html><h4>Java Regular Expressions Quantifier Reference</h4></html>"),BorderLayout.NORTH);
        quantHelpPanel.add(new JLabel("<html><table>" +
                "<tr><td><strong><u>Quantifier</u></strong></td><td><strong><u>Meaning</u></strong></td></tr>" +
                "<tr><td>[<em>xyz</em>]</td><td>Any character in the set</td></tr>" +
                "<tr><td>[^<em>xyz</em>]</td><td>Any character not in the set</td></td></tr>" +
                "<tr><td>[<em>x</em>-<em>z</em>]</td><td>Any character in the range <em>x</em> to <em>z</em></td></tr>" +
                "<tr><td>(<em>xyz</em>)</td><td>A group of characters</td></tr>" +
                "<tr><td>^</td><td>Marks the begining of the line</td></tr>" +
                "<tr><td>$</td><td>Marks the end of the line</td></tr>" +
                "<tr><td><em>x</em>?</td><td><em>x</em> is optional (once or not at all)</td></tr>" +
                "<tr><td><em>x</em>*</td><td><em>x</em> zero or more times in a row</td></tr>" +
                "<tr><td><em>x</em>+</td><td><em>x</em> one or more times in a row</td></tr>" +
                "<tr><td><em>x</em>{<em>n</em>}</td><td><em>x</em> exactly <em>n</em> times</td></tr>" +
                "<tr><td>$0</td><td><em>when replacing</em> refers to the matched text</td></tr>" +
                "<tr><td>$1-$9</td><td><em>when replacing</em> refers to groups, '()', in the matched text</td></tr>" +
                "</table><br></html>"),BorderLayout.CENTER);
        quantHelpPanel.add(new SwingLink("Full Java RegEx Documentation",
        "http://download.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html#sum"),BorderLayout.SOUTH);
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
                if(!loadProperties(file))
                    warning("Failed to Open",
                            "The search you tried to open appears to be missing or corrupt, and could not be opened.");
            }
        } else if(src == saveMItem){
            int returnVal = saveChooser.showSaveDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = saveChooser.getSelectedFile();
                if(!saveProperties(file,"User-Saved Grep State"))
                    warning("Failed to Save",
                            "The search could not be saved to that location.");
            }
        } else if(src == replaceMItem){
            if(result == null || result.size() == 0){
                warning("No results found","There is no active result set to replace text on.");
                return;
            }
            String rep = JOptionPane.showInputDialog(this, new JLabel("<html>" + 
                    "<h4>What text would you like to replace matches with?</h4>" +
                    "<p>Use '$0' to reference the match.</p><br>" +
                    "<p><strong>Warning:</strong> this is a destructive operation, and there is a potential for data loss.<br>" +
                    "Be sure to have a backup before running this command.</p><br>" +
                    "</html>")
                    , "Replace Matches"
                    , JOptionPane.QUESTION_MESSAGE);
            if(rep != null)
                try {
                    Grep.replace(result, rep);
                } catch(IllegalArgumentException e){
                    warning("Invalid Replace String","The string you wish to replace matches with is invalid.  Most likely, you need" +
                            "to replace any '$' mentions with '\\$'.");
                } catch (IOException e) {
                    warning("Replace Operation Failed","Running replace failed.  jGrep has tried to save the original state of affected files" +
                            "in *.orig files, you may need to manually repair them.\n\nThe error reported was: "+
                            e.getClass().getName()+": "+e.getMessage());
                }
        }
        // help menu
        else if(src == charHelpMItem){
            JOptionPane.showMessageDialog(this, charHelpPanel, "RegEx Character Help", JOptionPane.PLAIN_MESSAGE);
        } else if(src == quantHelpMItem){
            JOptionPane.showMessageDialog(this, quantHelpPanel, "RegEx Quantifier Help", JOptionPane.PLAIN_MESSAGE);
        }
        // about menu
        else if(src == aboutMItem){
            try {
                SwingLink.open(new URI("http://www.digitalgemstones.com/code/tools/jGrep.php"));
            } catch (URISyntaxException e) {
                throw new RuntimeException("Invalid link to DigitalGemstones.");
            }
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
        if(src == fileTable.getSelectionModel()){
            if (evt.getValueIsAdjusting() == false) {
                if (fileTable.getSelectedRow() == -1) {
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
        File f = fileTableModel.getFileAt(fileTable.getSelectedRow());
        resultPane.setText(grepToHTML(f,(Integer)contextSpinner.getValue()));
        resultPane.setSelectionStart(0);
        resultPane.setSelectionEnd(0);
    }
    
    private void warning(String title, String message){
        JOptionPane.showMessageDialog(this, message, title,JOptionPane.WARNING_MESSAGE);
    }
    
    class FileListModel extends AbstractTableModel {
        private static final long serialVersionUID = -7045093302929107928L;
        private ArrayList<File> files = new ArrayList<>();
        private String rootPath = "";

        public void setFiles(Set<File> f, String rp) {
            files = new ArrayList<>(f);
            rootPath = rp;
            Collections.sort(files);
            fireTableStructureChanged();
            fileTable.getColumnModel().getColumn(1).setPreferredWidth(25);
        }
        
        @Override
        public String getColumnName(int col) {
            if(col == 0)
                return "Files";
            else if(col == 1)
                return "Matches";
            throw new RuntimeException("Attempted to get out of bounds column");
        }

        @Override
        public Object getValueAt(int row, int col) {
            if(col == 0){
                String file = files.get(row).getAbsolutePath();
                if(file.equals(rootPath)) // if the root /is/ the file
                    return files.get(row).getName();
                return files.get(row).getAbsolutePath().substring(rootPath.length()+1);
            } else if(col == 1){
                return result.get(files.get(row)).size();
            }
            throw new RuntimeException("Attempted to get out of bounds column");
        }
        
        public File getFileAt(int row){
            if(row < 0)
                return null;
            return files.get(fileTable.convertRowIndexToModel(row));
        }

        @Override
        public int getRowCount() {
            return files.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }
        
    }
    
    private static class TSeparator extends JSeparator {
        private static final long serialVersionUID = -8339080768468333094L;

        TSeparator(int orient){
            super(orient);
            setPreferredSize(new Dimension(2,20));
        }
    }
    
    private static HashMap<Character,String> htmlEscapeMap = new HashMap<>();
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
        out.append(htmlEscape(text.substring(nextChar)));
        return out.toString();
    }
    
    @SuppressWarnings("unused")
    public static void main(String[] args){
        new JGrep(args.length == 0);
    }
}
