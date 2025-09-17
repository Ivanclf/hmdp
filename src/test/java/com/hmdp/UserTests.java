package com.hmdp;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import java.util.Set;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@SpringBootTest
class UserTests {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String TOKENS_FILE_PATH = "./src/test/java/com/hmdp/resources/tokens.txt";
    private static final int USER_COUNT = 1000;

    @Test
    void createUsers() throws IOException {
        // 存储所有用户的token
        List<String> tokens = new ArrayList<>(USER_COUNT);
        
        // 批量创建用户
        for (int i = 0; i < USER_COUNT; i++) {
            // 生成手机号
            String phone = "135" + String.format("%08d", i);
            
            // 发送验证码
            Result codeResult = userService.sendCode(phone, null);
            if (!codeResult.getSuccess()) {
                System.out.println("发送验证码失败：" + phone);
                continue;
            }

            // 模拟登录
            LoginFormDTO loginForm = new LoginFormDTO();
            loginForm.setPhone(phone);
            loginForm.setCode(stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone));
            Result loginResult = userService.login(loginForm, null);
            if (!loginResult.getSuccess()) {
                System.out.println("登录失败：" + phone);
                continue;
            }
            String token = loginResult.getData().toString();
            tokens.add(token);
        }

        // 将tokens写入文件
        System.out.println("准备写入tokens到文件，共有" + tokens.size() + "个token");
        
        // 确保目录存在
        File tokenFile = new File(TOKENS_FILE_PATH);
        if (!tokenFile.getParentFile().exists()) {
            tokenFile.getParentFile().mkdirs();
        }
        
        // 写入tokens
        try (FileWriter writer = new FileWriter(TOKENS_FILE_PATH)) {
            for (String token : tokens) {
                System.out.println("写入token: " + token);
                writer.write(token + System.lineSeparator());
                writer.flush(); // 确保立即写入
            }
        }
        
        // 验证文件是否写入成功
        File file = new File(TOKENS_FILE_PATH);
        if (file.exists() && file.length() > 0) {
            System.out.println("文件写入成功，文件大小: " + file.length() + " 字节");
            // 读取文件内容进行验证
            List<String> writtenTokens = Files.readAllLines(Paths.get(TOKENS_FILE_PATH));
            System.out.println("成功写入 " + writtenTokens.size() + " 个tokens到文件");
        } else {
            System.out.println("警告：文件写入可能失败，文件不存在或为空");
        }
        
        System.out.println("成功创建" + tokens.size() + "个用户，tokens处理完成");
    }

    @Test
    void deleteUsers() throws IOException {
        // 1. 批量删除用户和相关Redis数据
        for (int i = 0; i < USER_COUNT; i++) {
            String phone = "135" + String.format("%08d", i);
            
            // 1.1 删除Redis中的验证码
            stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
            
            // 1.2 删除用户
            User user = userService.query().eq("phone", phone).one();
            if (user != null) {
                userService.removeById(user.getId());
                if ((i + 1) % 100 == 0) {
                    System.out.println("已删除" + (i + 1) + "个用户");
                }
            }
        }

        // 2. 删除Redis中的所有用户token
        Set<String> keys = stringRedisTemplate.keys(LOGIN_USER_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            System.out.println("已删除Redis中的" + keys.size() + "个token");
        }

        // 3. 删除tokens文件
        File tokensFile = new File(TOKENS_FILE_PATH);
        if (tokensFile.exists()) {
            Files.delete(Paths.get(TOKENS_FILE_PATH));
            System.out.println("tokens文件已删除");
        }
        
        System.out.println("用户删除完成");
    }

    @Test
    void createGroup() {
        stringRedisTemplate.opsForStream().createGroup("stream.orders", ReadOffset.from("0"), "g1");
    }
}
