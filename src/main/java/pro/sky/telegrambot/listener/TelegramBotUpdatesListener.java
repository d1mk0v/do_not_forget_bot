package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    @Autowired
    private TelegramBot telegramBot;

    @Autowired
    private final NotificationTaskRepository notificationTaskRepository;

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    public TelegramBotUpdatesListener(NotificationTaskRepository notificationTaskRepository) {
        this.notificationTaskRepository = notificationTaskRepository;
    }

    @Override
    public int process(List<Update> updates) {

        updates.forEach(update -> {
            logger.info("Processing update: {}", update);

            String incomingMessage = update.message().text();
            Long chatId = update.message().chat().id();
            String welcomeMessage = update.message().chat().firstName() +
                    ", привет! Я бот-помощник. Я напомню тебе о важных делах!" +
                    "Добавь напоминание в формате: dd.mm.yyyy HH:mm Текст напоминания";

            if (incomingMessage.equals("/start")) {
                SendMessage message = new SendMessage(chatId, welcomeMessage);
                SendResponse response = telegramBot.execute(message);

                System.out.println(welcomeMessage);
            }

            parseCreatedReminder(incomingMessage, chatId);
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    public void parseCreatedReminder (String incomingMessage, Long chatId) {

        Pattern pattern = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)([\\W+]+)");
        Matcher matcher = pattern.matcher(incomingMessage);
        String responseMessage = "Напоминание добавлено!";


        if (matcher.matches()) {

            String date = matcher.group(1);
            String message = matcher.group(3);

            LocalDateTime date_and_time;
            try {
                date_and_time = LocalDateTime.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            } catch (DateTimeParseException e) {
                telegramBot.execute(new SendMessage(chatId, "Неверный формат даты и времени"));
                return;
            }

            notificationTaskRepository.save(new NotificationTask(chatId, message, date_and_time));

            SendMessage answer = new SendMessage(chatId, responseMessage);
            SendResponse response = telegramBot.execute(answer);
        }
    }

    @Scheduled(cron = "0 0/1 * * * *")
    public void schedulingLookForRecordsInDatabase() {
        LocalDateTime time = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        List<NotificationTask> tasks = notificationTaskRepository.findByDateAndTime(time);
        sendReminderMessageToUser(tasks);
    }

    public void sendReminderMessageToUser(List<NotificationTask> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            Long chatId = tasks.get(i).getChatId();
            String answer = tasks.get(i).getMessage();

            SendMessage reminderMessage = new SendMessage(chatId, answer);
            SendResponse response = telegramBot.execute(reminderMessage);
        }
    }
}
