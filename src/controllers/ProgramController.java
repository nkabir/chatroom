package controllers;

import javax.swing.SwingUtilities;

import views.LoginFrame;

//FIXME - Makes views resize better
//FIXME - Handle topic deletions (remove them from main menu and kick all users?)
//FIXME - Handle users alt+f4'ing - (use 60 second leases?)
public class ProgramController {
	public static void main(String[] args) throws InterruptedException {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new LoginFrame();
			}
		});
	}
}
