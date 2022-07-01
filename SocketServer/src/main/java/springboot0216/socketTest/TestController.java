package springboot0216.socketTest;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.springframework.stereotype.Component;

@ServerEndpoint(value = "/testcontroller/{id}")
@Component
public class TestController {
	
    //保存用户的session
    private static ConcurrentHashMap<String, TestController> webSocketSet = new ConcurrentHashMap<>();

    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;
    private String id = "";

    @OnOpen
    public void onOpen(@PathParam(value = "id") String id, Session session) {
        this.session = session;
        this.id = id;//接收到发送消息的人员编号
        webSocketSet.put(id, this);     //加入set中

        try {
            sendMessage("连接成功");
        } catch (IOException e) {

        }
    }

    @SuppressWarnings("unlikely-arg-type")
	@OnClose
    public void onClose() {
        webSocketSet.remove(this);  //从set中删除
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        //可以自己约定字符串内容，比如 内容|0 表示信息群发，内容|X 表示信息发给id为X的用户
        String sendMessage = message.split("[&]")[0];
        String sendUserId = message.split("[&]")[1];
        try {
            if(sendUserId.equals("0"))
                sendtoAll(sendMessage);
            else
                sendtoUser(sendMessage,sendUserId);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    //getAsyncRemote()和getBasicRemote() 异步和同步发送/
    public void sendMessage(String message) throws IOException {
        this.session.getAsyncRemote().sendText(message);
    }

    //发送信息给指定ID用户，如果用户不在线则返回不在线信息给自己
    public void sendtoUser(String message,String sendUserId) throws IOException {
        if (webSocketSet.get(sendUserId) != null) {
            if(!id.equals(sendUserId))
                webSocketSet.get(sendUserId).sendMessage( "用户" + id + "发来消息：" + " <br/> " + message);
            else
                webSocketSet.get(sendUserId).sendMessage(message);
        } else {
            //如果用户不在线则返回不在线信息给自己
            sendtoUser("当前用户不在线",id);
        }
    }

    //发送信息给所有人
    public void sendtoAll(String message) throws IOException {
        for (String key : webSocketSet.keySet()) {
            try {
                webSocketSet.get(key).sendMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
