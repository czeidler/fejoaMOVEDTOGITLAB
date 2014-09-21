package org.fejoa.gui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.*;

public class CreateProfileDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPasswordField passwordField;
    private JPasswordField reenterPasswordField;
    private JTextField userNameTextField;
    private JTextField serverTextField;
    private int result = JOptionPane.CANCEL_OPTION;

    public CreateProfileDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.setEnabled(false);
        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

// call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


        DocumentListener passwordListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                onPasswordEntered();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                onPasswordEntered();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                onPasswordEntered();
            }
        };

        passwordField.getDocument().addDocumentListener(passwordListener);
        reenterPasswordField.getDocument().addDocumentListener(passwordListener);
    }

    private void onPasswordEntered() {
        String password1 = getPassword();
        String password2 = new String(reenterPasswordField.getPassword());
        if (!password1.equals("") && password1.equals(password2))
            buttonOK.setEnabled(true);
        else
            buttonOK.setEnabled(false);
    }

    private void onOK() {
// add your code here
        result = JOptionPane.OK_OPTION;
        dispose();
    }

    private void onCancel() {
// add your code here if necessary
        dispose();
    }

    public int getResult() {
        return result;
    }

    public String getUserName() {
        return new String(userNameTextField.getText());
    }

    public String getServerName() {
        return new String(serverTextField.getText());
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public static void main(String[] args) {
        CreateProfileDialog dialog = new CreateProfileDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }
}
