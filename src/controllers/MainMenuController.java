package controllers;

import java.awt.Frame;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.apache.commons.lang3.StringUtils;

import listeners.TopicAddedRemoteEventListener;
import listeners.TopicRemovedRemoteEventListener;
import models.JMSTopic;
import models.JMSTopicDeleted;
import models.JMSTopicUser;
import models.JMSUser;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;
import net.jini.core.transaction.TransactionException;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.space.JavaSpace05;
import services.SpaceService;
import services.TopicService;
import services.helper.EntryLookupHelper;
import views.ChatroomFrame;
import views.LoginFrame;
import views.MainMenuFrame;

/**
 * Handles all of the logic of a given MainMenuFrame for a given user
 * 
 * @author Jonathan Sterling
 *
 */
public class MainMenuController {
	private static TopicService topicService = TopicService.getTopicService();

	private MainMenuFrame frame;
	private DefaultTableModel topicsTableModel;
	private JMSUser user;
	private RemoteEventListener topicAddedListenerStub;
	private RemoteEventListener topicRemovedListenerStub;
	private EventRegistration topicAddedRegistration;
	private EventRegistration topicRemovedRegistration;

	public MainMenuController(MainMenuFrame frame, JMSUser user) {
		this.frame = frame;
		this.user = user;

		// Listen for topics being created and deleted.
		registerTopicAddedListener();
		registerTopicRemovedListener();
	}

	/**
	 * Handles the topic creation button being pressed.
	 */
	public void handleCreateButtonPressed() {
		// Ask the user for a topic name.
		String topicName = JOptionPane.showInputDialog(frame, "Enter a topic name: ");

		if (topicName.replaceAll("[^A-Za-z0-9]", "").length() == 0) {
			JOptionPane.showMessageDialog(frame, "Topic name must have at least one alphanumeric character");
		} else if (StringUtils.isBlank(topicName)) {
			JOptionPane.showMessageDialog(frame, "Topic name cannot be blank");
		} else {
			// Create the topic.
			createTopic(topicName);
		}
	}

	/**
	 * Join a given topic ID
	 * 
	 * @param topicId
	 *            The UUID of the topic to join.
	 */
	public void handleJoinTopicPressed(UUID topicId) {
		JMSTopic topic = topicService.getTopicById(topicId);
		if (topic != null) {
			List<JMSTopicUser> topicUsers = topicService.getAllTopicUsers(topic);

			// Check if this user already has a chat window open for this topic
			for (JMSTopicUser topicUser : topicUsers) {
				if (topicUser.getUser().equals(user)) {
					JOptionPane.showMessageDialog(frame,
							"You are already in this topic.  If you are not, try logging out then back in to continue.");

					return;
				}
			}

			// If the topic the user wishes to join exists, and they're not
			// already in it open up a new ChatroomFrame for the topic
			new ChatroomFrame(topic, user);
		} else {
			// If the topic the user wishes to join does not exist, show an
			// error
			JOptionPane.showMessageDialog(frame,
					"Failed to Join Topic.  " + "Please refresh the topic list and try again");
		}
	}

	/**
	 * If a user tries to delete a topic, this method ensures they have the
	 * correct permissions, then removes the topic if they do.
	 * 
	 * @param tableModelRow
	 *            The row in the topics list that the user opted to delete.
	 * @param topicId
	 *            The UUID of the topic to be deleted.
	 */
	public void handleDeleteTopicPressed(int tableModelRow, UUID topicId) {
		JMSTopic topic = topicService.getTopicById(topicId);

		if (topic != null) {
			// If the topic exists, attempt deletion
			try {
				topicService.deleteTopic(topic, user);
			} catch (AccessDeniedException e) {
				JOptionPane.showInternalMessageDialog(frame,
						"Failed to delete topic.  " + "You are not the topic owner", "Topic Deletion Failed",
						JOptionPane.ERROR_MESSAGE, null);
			}

		} else {
			// If the topic does not exist, show an error message.
			JOptionPane.showInternalMessageDialog(frame,
					"Failed to delete topic.  " + "Topic does not exist.  Please try refreshing the topic list.",
					"Topic Deletion Failed", JOptionPane.ERROR_MESSAGE, null);
		}
	}

	/**
	 * Gets all of the topics in the space and puts them into a
	 * DefaultTableModel
	 * 
	 * @return A DefaultTableModel containing all of the topics in the space.
	 */
	public DefaultTableModel generateTopicTableModel() {
		Object[] columns = { "Topic", "Owner", "Owner ID", "Topic ID" };
		List<JMSTopic> topics = topicService.getAllTopics();

		Object[][] data = {};

		// Put all of the topics into an array of arrays.
		if (topics != null && topics.size() > 0) {
			data = new Object[topics.size()][5];
			for (int i = 0; i < topics.size(); i++) {
				data[i][0] = topics.get(i).getName();
				data[i][1] = topics.get(i).getOwner().getName();
				data[i][2] = topics.get(i).getOwner().getId();
				data[i][3] = topics.get(i).getId();
			}
		}

		// Generate a table model from the array of arrays.
		topicsTableModel = new DefaultTableModel(data, columns);

		return topicsTableModel;
	}

	/**
	 * A simple getter for the DefaultTableModel of topics.
	 * 
	 * @return The DefaultTableModel of topics
	 */
	public DefaultTableModel getTopicTableModel() {
		return topicsTableModel;
	}

	/**
	 * When a user clicks logout, this method closes any open topic chatrooms,
	 * closes the main menu, and opens a new login window
	 */
	public void logout() {
		cancelLeases();
		removeUserFromAllTopics();

		// Hide this window so its overridden dispose method isn't called (this
		// would cause an infinite loop)
		frame.setVisible(false);
		disposeAllVisibleWindows();

		frame.superDispose();

		new LoginFrame();
	}

	/**
	 * When a user closes the main menu, this method cancels the main menu
	 * listener leases, removes the user from any topic they're currently in,
	 * closes all open windows, then exists the application.
	 */
	public void handleDispose() {
		cancelLeases();
		removeUserFromAllTopics();

		// Hide this window so its overridden dispose method isn't called (this
		// would cause an infinite loop)
		frame.setVisible(false);
		disposeAllVisibleWindows();

		System.exit(0);
	}

	/**
	 * Disposes all visible windows
	 */
	private void disposeAllVisibleWindows() {

		Frame[] frames = Frame.getFrames();

		for (Frame frame : frames) {
			if (frame.isVisible()) {
				frame.setVisible(false);
				frame.dispose();
			}
		}
	}

	/**
	 * Removes the topic added/deleted listeners from the space.
	 */
	private void cancelLeases() {
		try {
			topicAddedRegistration.getLease().cancel();
			topicRemovedRegistration.getLease().cancel();
		} catch (UnknownLeaseException | RemoteException | NullPointerException e) {
			System.err.println("Failed to cancel MainMenuController lease(s)");
		}
	}

	/**
	 * Remove user from all topics, if they're in any. This is effectively so if
	 * a user crashes out, they can reset their state in all topics by logging
	 * in then back out again.
	 */
	private void removeUserFromAllTopics() {
		EntryLookupHelper lookupHelper = new EntryLookupHelper();
		JMSTopicUser thisUser = new JMSTopicUser();
		thisUser.setUser(user);
		List<JMSTopicUser> topicUsers = lookupHelper.findAllMatchingTemplate(SpaceService.getSpace(), thisUser);
		for (JMSTopicUser topicUser : topicUsers) {
			TopicService.getTopicService().removeTopicUser(topicUser.getTopic(), topicUser.getUser());
		}
	}

	/**
	 * Gets all of the topics in the space and shows them to the user.
	 * 
	 * @param table
	 *            A JTable to show the topics in.
	 */
	public void updateTopicList(JTable table) {
		topicsTableModel = generateTopicTableModel();
		table.setModel(topicsTableModel);
		table.removeColumn(table.getColumnModel().getColumn(MainMenuFrame.COLUMN_INDEX_OF_TOPIC_ID));
		table.removeColumn(table.getColumnModel().getColumn(MainMenuFrame.COLUMN_INDEX_OF_TOPIC_OWNER_ID));
	}

	/**
	 * Sets up listener for topics being added to the space.
	 */
	private void registerTopicAddedListener() {
		JavaSpace05 space = SpaceService.getSpace();
		JMSTopic template = new JMSTopic();
		ArrayList<JMSTopic> templates = new ArrayList<JMSTopic>(1);
		templates.add(template);

		try {
			Exporter myDefaultExporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(),
					false, true);

			TopicAddedRemoteEventListener eventListener = new TopicAddedRemoteEventListener(this);
			topicAddedListenerStub = (RemoteEventListener) myDefaultExporter.export(eventListener);

			topicAddedRegistration = space.registerForAvailabilityEvent(templates, null, true, topicAddedListenerStub,
					Lease.FOREVER, null);
		} catch (TransactionException | IOException e) {
			System.err.println("Failed to get new topic(s)");
			e.printStackTrace();
		}
	}

	/**
	 * Sets up listener for topics being removed from the space.
	 */
	private void registerTopicRemovedListener() {
		JavaSpace05 space = SpaceService.getSpace();
		JMSTopicDeleted template = new JMSTopicDeleted();
		ArrayList<JMSTopicDeleted> templates = new ArrayList<JMSTopicDeleted>(1);
		templates.add(template);

		try {
			// create the exporter
			Exporter myDefaultExporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(),
					false, true);

			// register this as a remote object
			// and get a reference to the 'stub'
			TopicRemovedRemoteEventListener eventListener = new TopicRemovedRemoteEventListener(this);
			topicRemovedListenerStub = (RemoteEventListener) myDefaultExporter.export(eventListener);

			topicRemovedRegistration = space.registerForAvailabilityEvent(templates, null, true,
					topicRemovedListenerStub, Lease.FOREVER, null);
		} catch (TransactionException | IOException e) {
			System.err.println("Failed to get new topic(s)");
			e.printStackTrace();
		}
	}

	/**
	 * Creates a topic with a given name.
	 * 
	 * @param name
	 *            The desired name of the topic to create.
	 */
	private void createTopic(String name) {
		JMSTopic topic = new JMSTopic(name, user);

		try {
			topicService.createTopic(topic);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame, "Failed to create topic.  Topic name already exists");
		}
	}
}
