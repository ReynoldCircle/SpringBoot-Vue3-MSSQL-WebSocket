package com.meetu.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetu.config.GoogleOauthConfig;

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class GoogleOAuth2NativeHttpController {
  // 原生使用(okhttp) http request, response 執行 OAuth2 的寫法

  @Autowired
  private GoogleOauthConfig googleOauth2Config;

  @Autowired
  private RestTemplate restTemplate;

  private final String scope = "https://www.googleapis.com/auth/userinfo.email";

  @GetMapping("/google-login")
  public String googleLogin(HttpServletResponse response) {

    // 直接 redirect 導向 Google OAuth2 API
    String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
        "client_id=" + googleOauth2Config.getClientId() +
        "&response_type=code" +
        "&scope=openid%20email%20profile" +
        "&redirect_uri=" + googleOauth2Config.getRedirectUri() +
        "&state=state";
    return "redirect:" + authUrl;
  }


  @GetMapping("/google-callback")
  public String oauth2Callback(@RequestParam(required = false) String code)
      throws IOException {
    if (code == null) {
      String authUri = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code" +
          "&client_id=" + googleOauth2Config.getClientId() +
          "&redirect_uri=" + googleOauth2Config.getRedirectUri() +
          "&scope=" + scope;
      return "redirect:" + authUri;
    } else {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> map = new LinkedMultiValueMap<>(); // key 可能重複再用
      map.add("code", code);
      map.add("client_id", googleOauth2Config.getClientId());
      map.add("client_secret", googleOauth2Config.getClientSecret());
      map.add("redirect_uri", googleOauth2Config.getRedirectUri());
      map.add("grant_type", "authorization_code");

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

      ResponseEntity<String> response = restTemplate.postForEntity("https://oauth2.googleapis.com/token", request,
          String.class);
      String credentials = response.getBody();
      // System.out.println("credentials" + credentials);

      JsonNode jsonNode = new ObjectMapper().readTree(credentials);
      String accessToken = jsonNode.get("access_token").asText();
      String idToken = jsonNode.get("id_token").asText();
//      System.out.println(accessToken);
//      System.out.println(idToken);
      
      HttpHeaders headers2 = new HttpHeaders();
      HttpHeaders headers3 = new HttpHeaders();
      headers2.setBearerAuth(accessToken);
      headers3.setBearerAuth(idToken);
      
      HttpEntity<String> entity = new HttpEntity<>(headers2);
      HttpEntity<String> entity2 = new HttpEntity<>(headers3);
//      System.out.println(entity.getHeaders().getFirst("Authorization"));
//      System.out.println(entity2.getHeaders().getFirst("Authorization"));
      ResponseEntity<String> response2 = restTemplate.exchange(
          "https://www.googleapis.com/oauth2/v1/userinfo?alt=json",
          HttpMethod.GET,
          entity,
          String.class);
      
      String payloadResponse = response2.getBody();
      
      JsonNode payloadJsonNode = new ObjectMapper().readTree(payloadResponse);

      // Extract data from payloadJsonNode and process it
      String payloadGoogleId = payloadJsonNode.get("id").asText();
      String payloadEmail = payloadJsonNode.get("email").asText();
      String payloadName = payloadJsonNode.get("name").asText();
      String payloadPicture = payloadJsonNode.get("picture").asText();
//      String payloadLocale = payloadJsonNode.get("locale").asText();

      // System.out.println("payloadGoogleId: " + payloadGoogleId);
      // System.out.println("payloadEmail: " + payloadEmail);
      // System.out.println("payloadName: " + payloadName);
      // System.out.println("payloadPicture: " + payloadPicture);
      // System.out.println("payloadLocale: " + payloadLocale);

//      LoginUserDTO loginUser = new LoginUserDTO(payloadGoogleId, payloadEmail, true);
      Map<String, String> userData = new HashMap<>();
      userData.put("token", entity2.getHeaders().getFirst("Authorization"));
      userData.put("googleId", payloadGoogleId);
      userData.put("email", payloadEmail);
      userData.put("name", payloadName);
      userData.put("picture", payloadPicture);
//      userData.put("locale", payloadLocale);
      
      // 將使用者放到資料庫
      // todo...

      // Convert the map to a JSON string
      String userDataJson = new ObjectMapper().writeValueAsString(userData);
      
      // 对 JSON 字符串进行 URL 编码
      String encodedUserData = URLEncoder.encode(userDataJson, StandardCharsets.UTF_8.toString());
      // 將 USER 放到 SESSION 內
//      httpSession.setAttribute("loginUser", loginUser);

      return "redirect:http://localhost:5173/?userData=" + encodedUserData;
    }

  }

}