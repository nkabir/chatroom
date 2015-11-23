package controllers;

import javax.swing.SwingUtilities;

import listeners.MessageRemoteEventListener;
import services.SpaceService;
import views.LoginFrame;

//FIXME - Makes views resize better
public class ProgramController {
	public static void main(String[] args) throws InterruptedException {
		// SpaceService.addCodeBaseFor(MessageRemoteEventListener.class);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new LoginFrame();
			}
		});
	}
}
