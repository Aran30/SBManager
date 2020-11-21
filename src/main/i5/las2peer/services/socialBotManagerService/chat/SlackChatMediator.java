package i5.las2peer.services.socialBotManagerService.chat;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.OptionalLong;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.websocket.DeploymentException;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ser.std.StdKeySerializers.Default;
// TODO: Currently needed because of class with the same name in this package
import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.response.channels.UsersLookupByEmailResponse;
import com.github.seratch.jslack.api.methods.response.chat.ChatPostMessageResponse;
import com.github.seratch.jslack.api.methods.response.conversations.ConversationsListResponse;
import com.github.seratch.jslack.api.methods.response.files.FilesUploadResponse;
import com.github.seratch.jslack.api.methods.response.users.UsersConversationsResponse;
import com.github.seratch.jslack.api.model.Conversation;
import com.github.seratch.jslack.api.model.ConversationType;
import com.github.seratch.jslack.api.model.block.LayoutBlock;
import com.github.seratch.jslack.api.model.block.SectionBlock;
import com.github.seratch.jslack.api.rtm.RTMClient;
import com.github.seratch.jslack.api.rtm.message.Message;
import com.github.seratch.jslack.api.rtm.message.Message.MessageBuilder;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONArray;
import sun.misc.BASE64Decoder;

public class SlackChatMediator extends ChatMediator {
	private Slack slack = null;
	private RTMClient rtm = null;
	private SlackChatMessageCollector messageCollector = new SlackChatMessageCollector();
	private String botUser;
	// this variable is only good when using a bot in a private conversation
	public static HashMap<String, String> usersByChannel; 
	// Is needed to use the token when downloading user files
	public static HashMap<String, String> botTokens = new HashMap<String, String>(); 
	public SlackChatMediator(String authToken) throws IOException, DeploymentException {
		super(authToken);
		this.slack = new Slack();
		this.rtm = this.slack.rtm(authToken);
		this.usersByChannel  = new HashMap<String,String>();
		this.rtm.addMessageHandler(messageCollector);
		this.rtm.connect();
		ArrayList<ConversationType> types = new ArrayList<ConversationType>();
		types.add(ConversationType.IM);types.add(ConversationType.MPIM);
		try {
			UsersConversationsResponse test = (slack.methods().usersConversations(req -> req.token(authToken).types(types)));
			System.out.println(test.getChannels());
			for(Conversation c : test.getChannels()) {
				botTokens.put(c.getId(), authToken);
			}
		} catch (IOException | SlackApiException e) {
			System.out.println("Could not retrieve bot channels because of " + e +". The bot will not be able to download sent files...");
		}
		this.botUser = rtm.getConnectedBotUser().toString();
		System.out.println(this.botUser + " connected.");
	}

	@Override
	public void sendMessageToChannel(String channel, String text, OptionalLong id) {
		MessageBuilder msg = Message.builder().id(System.currentTimeMillis()).channel(channel).text(text);
		if (id.isPresent()) {
			msg.id(id.getAsLong());
		}
		String message = msg.build().toJSONString();
		try {
			String userId =  (slack.methods().authTest(req -> req.token(authToken))).getUserId();
			String url = slack.methods().usersInfo(req -> req.token(authToken).user(userId)).getUser().getProfile().getImageOriginal();
			String name = slack.methods().usersInfo(req -> req.token(authToken).user(userId)).getUser().getName();
			ChatPostMessageResponse response = slack.methods(authToken).chatPostMessage(req -> req.channel(channel) // Channel
																													// ID
					.text(text).iconUrl(url).username(name));
			System.out.println("Message sent: " + response.isOk());
		/*	BufferedImage i = ConvertToImage("s");
			File output = new File("i.png");
			ImageIO.write(i, "png", output);
			if (!ImageIO.write(i, "png", output)) {
				  throw new RuntimeException("Unexpected error writing image");
				}
			System.out.println("file is: " + output.exists());
			System.out.println(output);
			ArrayList<String> channels = new ArrayList<String>();
			channels.add(channel);
			
			FilesUploadResponse response2  = slack.methods(authToken).filesUpload(req -> req.channels(channels).file(output).content("Pretty stuff").filename("filename").title("title"));
			System.out.println("File sent: " + response2.isOk());*/
		} catch (Exception e) {
			this.messageCollector.setConnected(false);
			this.reconnect();
			rtm.sendMessage(message);
			System.out.println("Sent message with Exception: " + e.getMessage());
			if(e.getMessage().toLowerCase().equals("timeout")) {
				sendMessageToChannel(channel, text, id);
			}
		}
		try {
			if(usersByChannel.get(channel) == null) {
				System.out.println("1");		
				String user = slack.methods().conversationsInfo(req -> req.token(authToken).channel(channel)).getChannel().getUser();
				System.out.println("2 + " + user);
				usersByChannel.put(channel, slack.methods().usersInfo(req -> req.token(authToken).user(user)).getUser().getProfile().getEmail());
				System.out.println("3");
			}
		} catch (Exception e) {
			System.out.println("Could not extract Email for reason + " + e);
		}
			
	}
	
	
	public BufferedImage ConvertToImage(String image) throws IOException{
		BASE64Decoder decoder = new BASE64Decoder();
        byte[] decodedBytes = decoder.decodeBuffer("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");
        System.out.println("Decoded upload data : " + decodedBytes.length);
	    BufferedImage imagede = ImageIO.read(new ByteArrayInputStream(decodedBytes));
	  //  File f = new File(uploadFile);
	    
        // write the image
      //   ImageIO.write(imagede, "png", f);
	    return imagede;
	}
	
	
	public File ConvertToImageFile(String image) throws IOException{
		BASE64Decoder decoder = new BASE64Decoder();
        byte[] decodedBytes = decoder.decodeBuffer("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");
        String uploadFile = "/tmp/test.png";
        System.out.println("Decoded upload data : " + decodedBytes.length);
	    BufferedImage imagede = ImageIO.read(new ByteArrayInputStream(decodedBytes));
	    File f = new File(uploadFile);
	    
        // write the image
         ImageIO.write(imagede, "png", f);
	    return f;
	}
	
	
	
	// static for calling from `SlackChatMessageCollector`
	public static ChatMessage parseSlackMessage(JSONObject o) throws InvalidChatMessageException {
		String channel = o.getAsString("channel");
		String user = o.getAsString("user");
		String text = o.getAsString("text");
		if(o.get("files") != null) {
			for(int i = 0; i < ((JSONArray) o.get("files")).size(); i++) {
				// left it as for(...),  but only sending 1 file at a time will be accepted currently
				try {
					URL url = new URL(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("url_private_download"));
					System.out.println(url.toString());
					HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
					httpConn.addRequestProperty("Authorization", "Bearer " + botTokens.get(channel));
					InputStream in =(InputStream) httpConn.getInputStream();
					FileOutputStream fileOutputStream = new FileOutputStream(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("title"));
					byte dataBuffer[] = new byte[Integer.valueOf(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("size"))];
				    int bytesRead;
				    while ((bytesRead = in.read(dataBuffer, 0, Integer.valueOf(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("size")))) != -1) {
				    	System.out.println(bytesRead);
				        fileOutputStream.write(dataBuffer, 0, bytesRead);
				    }
					String body = Base64.getEncoder().encodeToString(Files.readAllBytes(new File(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("title")).toPath()));
					fileOutputStream.close();
					Files.deleteIfExists(Paths.get(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("title")));
					return new ChatMessage(channel, user, text, ((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("title"), ((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("filetype"), body);
				}
				catch(Exception e){
					System.out.println("Could not extract File for reason " + e);
				}
				
			}
			
		}
		if (channel == null || user == null || text == null) {
			throw new InvalidChatMessageException();
		}

		return new ChatMessage(channel, user, text);
	}

	
	// static for calling from `SlackChatMessageCollector`
		public  String getToken(JSONObject o) throws InvalidChatMessageException {
			String channel = o.getAsString("channel");
			String user = o.getAsString("user");
			String text = o.getAsString("text");
			System.out.println("JSON i s "+ o);
			if(o.get("files") != null) {
				for(int i = 0; i < ((JSONArray) o.get("files")).size(); i++) {
					
					text="";
					String fileType = ((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("filetype");
					// left it as for(...),  but only sending 1 file at a time will be accepted currently
					try {
						URL url = new URL(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("url_private_download"));
						System.out.println(url.toString());
						HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
						httpConn.addRequestProperty("Authorization", "Bearer xoxb-1026213558273-1210571424002-peyvGa80nFKZKFim6royEc6a");
						InputStream in =(InputStream) httpConn.getInputStream();
						FileOutputStream fileOutputStream = new FileOutputStream(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("title"));
						byte dataBuffer[] = new byte[Integer.valueOf(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("size"))];
					    int bytesRead;
					    while ((bytesRead = in.read(dataBuffer, 0, Integer.valueOf(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("size")))) != -1) {
					    	System.out.println(bytesRead);
					        fileOutputStream.write(dataBuffer, 0, bytesRead);
					    }
						byte[] bytes = Base64.getEncoder().encode(Files.readAllBytes(new File(((JSONObject)((JSONArray) o.get("files")).get(i)).getAsString("title")).toPath()));
						System.out.print(bytes.toString());
					}
					catch(Exception e){
						System.out.println("Could not extract File for reason " + e);
					}
					
				}
			}
			//File file = new File(o.get);
			if (channel == null || user == null || text == null) {
				throw new InvalidChatMessageException();
			}

			return "s";
		}	
	
	@Override
	public Vector<ChatMessage> getMessages() {
		Vector<ChatMessage> messages = this.messageCollector.getMessages();
		this.reconnect();
		return messages;
	}
	
	/*public String getEmails(String channel) {
		
		if(usersByChannel.get(channel) == null)
		{
			return "No Email available at the moment";
		}
		System.out.println("Email is " + usersByChannel.get(channel));
		return usersByChannel.get(channel); // slack.methods().usersInfo(req -> req.token(authToken).user(user)).getUser().getProfile().getEmail();
	}
*/
	public String getBotUser() {
		return this.botUser.toString();
	}

	@Override
	public String getChannelByEmail(String email) {
		Slack slack = Slack.getInstance();
		try {
			UsersLookupByEmailResponse lookupByEmailResponse = slack.methods(this.authToken)
					.usersLookupByEmail(req -> req.email(email));
			String userId = lookupByEmailResponse.getUser().getId();
			ConversationsListResponse listResponse = slack.methods(this.authToken)
					.conversationsList(req -> req.excludeArchived(true).types(Arrays.asList(ConversationType.IM)));
			Conversation im = listResponse.getChannels().stream().filter(c -> c.getUser().equals(userId)).findFirst()
					.get();
			return im.getId();
		} catch (IOException | SlackApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private void reconnect() {
		if (!this.messageCollector.isConnected()) {
			try {
				this.rtm.close();
				this.slack = new Slack();
				this.rtm = this.slack.rtm(authToken);

				this.rtm.addMessageHandler(messageCollector);
				this.rtm.connect();
				this.botUser = rtm.getConnectedBotUser().toString();
				this.messageCollector.setConnected(true);
				System.out.println(this.botUser + " reconnected.");
			} catch (IOException | DeploymentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void sendFileMessageToChannel(String channel, File f, String text, OptionalLong id) {
		// TODO Auto-generated method stub

	}


	@Override
	public void close() {
		try {
			this.rtm.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.slack = null;
	}
}
