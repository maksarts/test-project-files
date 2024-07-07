package ru.maksarts;

import com.google.gson.Gson;
import lombok.Data;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final int requestLimit;
    private final TimeUnit timeUnit;

    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final RestTemplate restTemplate;
    private ArrayList<LocalDateTime> timestamps;


    public CrptApi(TimeUnit timeUnit, int requestLimit){
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        timestamps = new ArrayList<>();
        restTemplate = new RestTemplateBuilder().build();
    }

    //      В ТЗ не прописано, как именно используется подпись, поэтому
    // оставляю параметр sign для будущего расширения функционала
    //      Поскольку этот метод - единственная точка входа в класс, ключевого слова synchronized
    // будет достаточно для обеспечения потокобезопасности
    public synchronized CrptApiResponse makeRequest(Request request, String sign){
        if(approve(LocalDateTime.now())) {
            Gson gson = new Gson();
            String json = gson.toJson(request);
            ResponseEntity<CrptApiResponse> response = restTemplate.postForEntity(URL, json, CrptApiResponse.class);
            return response.getBody();
        }
        return null; // если метод возвращает null - значит превышен лимит запросов
    }


    //      Сохраняем таймстемпы последних requestLimit запросов, если пришел новый запрос и кол-во запросов
    // меньше лимита - одобряем операцию, если запросов requestLimit - смотрим дату самого раннего,
    // если с самого раннего из сохраненных запросов прошло больше времени, чем timeUnit, значит
    // место под запрос есть - обновляем список таймстемпов и одобряем операцию, иначе - блокируем.
    //      Согласно ТЗ, достаточно просто отклонить операцию, если лимит запросов в промежуток времени превышен.
    // Также может быть удобна реализация, в которой заблокированные запросы мы сохраняем и обрабатываем по истечению
    // необходимого времени, не превышая лимит, такое решение было бы удобно реализовать асинхронно, с помощью чтения запросов из
    // очереди, например, RabbitMQ (объект данного класса был бы подписан на очередь и обрабатывал бы из нее запросы
    // с необходимой нам частотой в единицу времени, сервис же, который данный запросы посылает, отправлял бы их в очередь)
    private boolean approve(LocalDateTime time){
        if (timestamps.size() < requestLimit){
            timestamps.add(time);
            return true;
        }
        else if (timestamps.get(0).plus(1, timeUnit.toChronoUnit()).isBefore(time)){
            timestamps.remove(0);
            timestamps.add(time);
            return true;
        }
        return false;
    }


    // В ТЗ не указан формат ответов от Честного знака,
    // а метод POST, как правило, предполагает какое-либо
    // тело ответа, поэтому предположим, что будет такое
    @Data
    public static class CrptApiResponse{
        public String status;
    }

    @Data
    public static class Description{
        public String participantInn;
    }

    @Data
    public static class Product{
        public String certificate_document;
        public String certificate_document_date;
        public String certificate_document_number;
        public String owner_inn;
        public String producer_inn;
        public String production_date;
        public String tnved_code;
        public String uit_code;
        public String uitu_code;
    }

    @Data
    public static class Request{
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public ArrayList<Product> products;
        public String reg_date;
        public String reg_number;
    }
}
