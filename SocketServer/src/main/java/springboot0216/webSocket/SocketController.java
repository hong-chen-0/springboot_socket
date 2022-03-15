package springboot0216.webSocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;

//参数role判断用户角色0是客服，1是用户
@ServerEndpoint(value = "/websocket/{role}")
@Component
public class SocketController {

  //用本地线程保存session
  private static ThreadLocal<Session> sessions = new ThreadLocal<Session>();
  //保存所有连接上的用户的session
  private static Map<String, SocketUserInfo> userSessionMap = new ConcurrentHashMap<>();
  //保存在线客服的session
  private static Map<String, SocketUserInfo> serverSessionMap = new ConcurrentHashMap<>();

  //连接
  @OnOpen
  public void onOpen(Session session, @PathParam(value="role") Integer role) {

      //默认返回错误状态
      Map<String, String> resultMap = new HashMap<>();
      resultMap.put("state", "error");

      //保证各个线程里的变量相对独立于其他线程内的变量
      sessions.set(session);

      //客服上线
      if (role.equals(0)) {

          //创建一个在线客服信息
          SocketUserInfo serverInfo = new SocketUserInfo();
          serverInfo.setSessionId(session.getId());
          serverInfo.setSession(session);
          serverInfo.setUserRole("客服");

          //告诉客服连接成功
          resultMap.put("state", "success");

          //去查询是否有排队中的用户
          //如果存在排队的用户，就将用户和客服绑定
          if (findLineUser() != null){
              SocketUserInfo userInfo = userSessionMap.get(findLineUser());
              //将用户绑定到客服
              serverInfo.setTargetSessionId(userInfo.getSessionId());
              //将客服绑定到用户
              userInfo.setTargetSessionId(serverInfo.getSessionId());
              userSessionMap.put(userInfo.getSessionId(), userInfo);
              System.out.println("客户"+ serverInfo.getSessionId() + "正在为用户" + userInfo.getSessionId()+"服务");

              Map<String, String> result = new HashMap<>();
              //客服显示用户信息
              result.put("msg", "正在为用户"+userInfo.getSessionId()+"服务！");
              sendMsg(serverInfo.getSession(), JSON.toJSONString(result));
              //告诉用户有客服为他服务
              result.put("msg", "客服"+serverInfo.getSessionId()+"正在为您服务！");
              sendMsg(userInfo.getSession(), JSON.toJSONString(result));
          }

          //将在线客服信息保存到map中
          serverSessionMap.put(session.getId(), serverInfo);
          System.out.println("客服：" + serverInfo.getSessionId() + "连接上服务器，当前在线客服共计：" + serverSessionMap.size());
      }
      if (role.equals(1)) {

          //创建一个在线用户信息
          SocketUserInfo userInfo = new SocketUserInfo();
          userInfo.setSessionId(session.getId());
          userInfo.setSession(session);
          userInfo.setUserRole("用户");

          //告诉用户连接成功
          resultMap.put("state", "success");

          //去查询是否有在线的客服
          //有空闲客服就将用户和客服绑定
          if (findFreeServer() != null){
              SocketUserInfo serverInfo = serverSessionMap.get(findFreeServer());
              //将用户绑定到客服
              serverInfo.setTargetSessionId(userInfo.getSessionId());
              serverSessionMap.put(serverInfo.getSessionId(), serverInfo);
              //将客服绑定到用户
              userInfo.setTargetSessionId(serverInfo.getSessionId());
              System.out.println("客户"+ serverInfo.getSessionId() + "正在为" + userInfo.getSessionId()+"服务");

              Map<String, String> result = new HashMap<>();
              //客服显示用户信息
              result.put("msg", "正在为用户"+userInfo.getSessionId()+"服务！");
              sendMsg(serverInfo.getSession(), JSON.toJSONString(result));
              result.put("msg", "客服"+serverInfo.getSessionId()+"正在为您服务！");
              sendMsg(userInfo.getSession(), JSON.toJSONString(result));
          } else {
              //告诉用户系统繁忙
              resultMap.put("msg", "系统繁忙！");
          }

          //将在线用户信息保存到map中
          userSessionMap.put(session.getId(), userInfo);
          System.out.println("用户编号：" + userInfo.getSessionId() + "连接上服务器，当前在线用户共计：" + userSessionMap.size());
      }
      //返回连接信息
      String result = JSON.toJSONString(resultMap);
      System.out.println(result);
      sendMsg(session, result);
  }

  //关闭连接
  @OnClose
  public void onClose(Session session) {
      SocketUserInfo serverInfo = serverSessionMap.get(session.getId());
      //客服下线
      if (serverInfo != null) {
          //将客户从map中移除
          serverSessionMap.remove(session.getId());

          //查看是否有服务服务对象
          if (null != serverInfo.getTargetSessionId()){
              //给用户说系统错误
              Map<String, String> result = new HashMap<>();
              result.put("msg", "系统错误，请刷新重试！");
              sendMsg(userSessionMap.get(serverInfo.getTargetSessionId()).getSession(), JSON.toJSONString(result));
          }
          System.out.println("客服编号：" + serverInfo.getSessionId() + "退出了连接，当前在线客服共计：" + serverSessionMap.size());
      } else {//用户下线
          //将用户从map中移除
          userSessionMap.remove(session.getId());

          //从客服中解绑
          for (SocketUserInfo serverSocketInfo: serverSessionMap.values()) {
              //查找绑定的客服，即客服绑定的用户不为空，并且绑定的用户id和现在下线的用户id一样
              if (serverSocketInfo.getTargetSessionId() != null && serverSocketInfo.getTargetSessionId().equals(session.getId())){
                  //解绑
                  serverSocketInfo.setTargetSessionId(null);
                  serverSessionMap.put(serverSocketInfo.getSessionId(), serverSocketInfo);
                  System.out.println("用户编号：" + session.getId() + "断开了与客服" + serverSocketInfo.getSessionId() + "的连接");

                  //客服解绑以后，可能还会有在线排队的用户，就让这个客服去
                  String lineUser = findLineUser();
                  if (lineUser != null){
                      //将用户绑定到客服
                      serverSocketInfo.setTargetSessionId(lineUser);
                      serverSessionMap.put(serverSocketInfo.getSessionId(), serverSocketInfo);
                      //将客服绑定到用户
                      userSessionMap.get(lineUser).setTargetSessionId(serverSocketInfo.getSessionId());
                      System.out.println("客户"+ serverSocketInfo.getSessionId() + "正在为" + lineUser+"服务");

                      Map<String, String> result = new HashMap<>();
                      //客服显示用户信息
                      result.put("msg", "正在为用户"+lineUser+"服务！");
                      sendMsg(serverSocketInfo.getSession(), JSON.toJSONString(result));
                      //用户显示客户信息
                      result.put("msg", "客服"+serverSocketInfo.getSessionId()+"正在为您服务！");
                      sendMsg(userSessionMap.get(lineUser).getSession(), JSON.toJSONString(result));
                  }
              }
          }
          System.out.println("用户编号：" + session.getId() + "退出了连接，当前在线用户共计：" + userSessionMap.size());
      }
  }

  //用户和客户端互相传递消息
  @OnMessage
  public void onMessage(String message, Session session) {
      //消息
      Map<String, String> result = new HashMap<>();

      SocketUserInfo serverInfo = serverSessionMap.get(session.getId());
      //客服消息
      if (serverInfo != null) {
          System.out.println("客服"+ session.getId()+"发送消息：\""+ message +"\"给用户"+serverSessionMap.get(session.getId()).getTargetSessionId());
          result.put("msg", "客服"+session.getId()+"："+message);
          //将消息发送给用户
          //要判断是否绑定到有用户如果有就将消息传递到用户
          if (null != serverSessionMap.get(session.getId()).getTargetSessionId()){
              sendMsg(userSessionMap.get(serverSessionMap.get(session.getId()).getTargetSessionId()).getSession(), JSON.toJSONString(result));
          } else {//如果没有就将消息给自己，嘻嘻嘻
              sendMsg(session, JSON.toJSONString(result));
          }

      } else {//用户消息
          System.out.println("用户"+ session.getId()+"发送消息：\""+ message +"\"给客户"+userSessionMap.get(session.getId()).getTargetSessionId());
          result.put("msg", "用户"+session.getId()+"："+message);
          //将消息发送给客服
          //判断是否绑定了客服，如果有就发送消息
          if (null != userSessionMap.get(session.getId()).getTargetSessionId()){
              sendMsg(serverSessionMap.get(userSessionMap.get(session.getId()).getTargetSessionId()).getSession(), JSON.toJSONString(result));
          } else{//同上
              sendMsg(session,JSON.toJSONString(result));
          }
      }
  }

  //异常
  @OnError
  public void onError(Session session, Throwable throwable) {
      System.out.println("发生异常!");
      throwable.printStackTrace();
  }



  //统一的发送消息方法
  private synchronized void sendMsg(Session session, String msg) {
      try {
          session.getBasicRemote().sendText(msg);
      } catch (IOException e) {
          e.printStackTrace();
      }
  }

  //查询排队用户
  private synchronized String findLineUser(){
      //判断是否有用户
      if (userSessionMap.size() > 0){
          //遍历所有用户，查找一个排队的用户
          for (SocketUserInfo UserInfo: userSessionMap.values()) {
              if (null == UserInfo.getTargetSessionId()){
                  return UserInfo.getSessionId();
              }
          }
      }
      return null;
  }

  //查询在线空闲客服
  private  synchronized String findFreeServer(){
      //判断是否有客服
      if (serverSessionMap.size() > 0){
          //遍历所有客服，查找一个空闲的客服
          for (SocketUserInfo serverInfo: serverSessionMap.values()) {
              if (null == serverInfo.getTargetSessionId()){
                  return serverInfo.getSessionId();
              }
          }
      }
      return null;
  }
}
