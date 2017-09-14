package burp;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * Created by mike on 7/24/17.
 */

public class FormHelp extends JFrame {

    public FormHelp() {
        URL htmlFile = FormHelp.class.getResource("/FormHelp.html");

        JEditorPane editorPane;
        try {
            editorPane = new JEditorPane(htmlFile);
        } catch (Exception e) {
            editorPane = new JEditorPane();
        }
        this.setTitle("Burplay Manual");
        editorPane.setEditable(false);
        JScrollPane editorScroll = new JScrollPane(editorPane);
        editorScroll.setPreferredSize(new Dimension(600,400));
        this.add(editorScroll);

    }

}
