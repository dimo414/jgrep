package grep;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;

import javax.swing.JLabel;
import javax.swing.JOptionPane;

/**
 * A JLabel that behaves like a hyperlink, launching the default browser when clicked.
 * 
 * The link is styled like a standard &lt;a&gt; tag in a browser; blue and underlined
 * by default, and changing color as the user interacts with it.
 */
// http://stackoverflow.com/q/527719/113632
public class SwingLink extends JLabel {
  private static final long serialVersionUID = 8273875024682878518L;

  private volatile String text;
  private final URI uri;
  private volatile LinkStyle inactive = LinkStyle.UNVISITED;

  /**
   * Constructs a SwingLink with the given text that will launch the given URI when clicked.
   * 
   * @throws IllegalArgumentException if uri is not a valid URI
   */
  public SwingLink(String text, String uri) {
    this(text, URI.create(uri));
  }

  /**
   * Constructs a SwingLink with the given text that will launch the given URI when clicked.
   */
  public SwingLink(String text, URI uri) {
    super(text);
    if (text == null || uri == null) {
      throw new NullPointerException();
    }
    this.uri = uri;
    setToolTipText(uri.toString());

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        open(uri);
        inactive = LinkStyle.VISITED;
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        updateText(LinkStyle.ACTIVE);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        updateText(inactive);
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
      }
    });
  }

  /**
   * Styles the text like a link, in addition to the default behavior.
   * 
   * {@inheritDoc}
   */
  @Override
  public void setText(String text) {
    if (text == null) {
      throw new NullPointerException();
    }
    this.text = text;
    // inactive is still null when called from JLabel's constructor
    updateText(inactive == null ? LinkStyle.UNVISITED : inactive);
    
  }

  private void updateText(LinkStyle style) {
    super.setText(style.format(text));
  }
  
  public URI getLink() {
    return uri;
  }

  public String getLinkText() {
    return text;
  }

  /**
   * Attempts to open a URI in the user's default browser, displaying a graphical warning message
   * if it fails.
   */
  public static void open(URI uri) {
    if (Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();
      try {
        desktop.browse(uri);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(null,
            "Failed to open " + uri + " - your computer is likely misconfigured.",
            "Cannot Open Link", JOptionPane.WARNING_MESSAGE);
      }
    } else {
      JOptionPane.showMessageDialog(null, "Java is not able to open a browser on your computer.",
          "Cannot Open Link", JOptionPane.WARNING_MESSAGE);
    }
  }
  
  private enum LinkStyle {
    UNVISITED(new Color(0x00, 0x00, 0x99), true),
    ACTIVE(new Color(0x99, 0x00, 0x00), false),
    VISITED(new Color(0x80, 0x00, 0x80), true);
    
    private static final String FORMAT_STRING =
        "<html><span style=\"color: #%02X%02X%02X;\">%s</span></html>";
    
    private final Color color;
    private final boolean underline;
    
    LinkStyle(Color c, boolean u) {
      color = c;
      underline = u;
    }

    public String format(String text) {
      String underlinedText = underline ? "<u>" + text + "</u>" : text;
      return String.format(
          FORMAT_STRING, color.getRed(), color.getGreen(), color.getBlue(), underlinedText);
    }
  }
}
