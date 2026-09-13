package com.learning.meetingrooms.room;

import com.learning.meetingrooms.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers(disabledWithoutDocker = true)
class RoomPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearRooms() {
        roomRepository.deleteAll();
    }

    @Test
    void flywayCreatesRoomsTable() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'rooms'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    void createsAndRetrievesRoomThroughHttpAndPostgres() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lotus Room","location":"Floor 3","capacity":8}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdBy").value("system"));

        assertThat(roomRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Lotus Room"));
    }

    @Test
    void databaseRejectsRoomNamesThatDifferOnlyByCase() throws Exception {
        String firstRoom = """
                {"name":"Nile Room","location":"Floor 2","capacity":12}
                """;
        String duplicateRoom = """
                {"name":"nile room","location":"Floor 4","capacity":20}
                """;

        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(firstRoom))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(duplicateRoom))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NAME_ALREADY_EXISTS"));
    }

    @Test
    void retrievesRoomByIdAndChangesItsStatus() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pyramid Room","location":"Floor 5","capacity":16}
                                """))
                .andExpect(status().isCreated());

        Long roomId = roomRepository.findAll().getFirst().getId();

        mockMvc.perform(patch("/api/rooms/{id}/status", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pyramid Room"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void exposesGeneratedOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Room Service API"))
                .andExpect(jsonPath("$.paths['/api/rooms']").exists());
    }
}
