package com.rookies5.Backend_MATE.entity.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 프론트엔드와 동일한 기술 스택 목록을 관리하는 상수 클래스
 */
public class TechStack {
    public static final List<String> OPTIONS = Collections.unmodifiableList(Arrays.asList(
            ".NET", "AWS", "Adobe XD", "Angular", "Ansible", "Apache", "Apollo", "Azure",
            "Babel", "Bootstrap", "C", "C#", "C++", "Canva", "Cassandra", "Chakra UI",
            "Confluence", "Cypress", "Dart", "Django", "Discord", "Docker", "DynamoDB", "Emotion",
            "Elasticsearch", "Express", "FastAPI", "Fastify", "Figma", "Firebase", "Flask", "Flutter",
            "Framer", "GCP", "Git", "GitHub", "GitHub Actions", "GitLab", "Go", "GraphQL",
            "HTML/CSS", "Illustrator", "Java", "JavaScript", "Jenkins", "Jest", "Jira", "Jotai",
            "Keras", "Koa", "Kotlin", "Kotlin (Android)", "Kubernetes", "Laravel", "Linux", "MariaDB",
            "Material UI", "Miro", "MobX", "MongoDB", "MQTT", "MySQL", "NestJS", "Netlify",
            "Next.js", "Nginx", "Node.js", "Notion", "NumPy", "Objective-C", "OpenCV", "Oracle",
            "Pandas", "PHP", "Photoshop", "Playwright", "PostgreSQL", "PyTorch", "Python", "R",
            "React", "React Native", "Recoil", "Redis", "Redux", "Ruby", "Ruby on Rails", "Rust",
            "SWR", "Scikit-learn", "Slack", "Socket.io", "Spring", "Spring Boot", "SQLite", "Storybook",
            "Styled Components", "Supabase", "Svelte", "Swift", "Tailwind CSS", "TanStack Query", "TensorFlow", "Terraform",
            "TypeScript", "Unity", "Unreal Engine", "Vercel", "Vite", "Vue.js", "Webpack", "WebSockets",
            "Zeplin", "Zustand", "gRPC"
    ));

    /**
     * 입력받은 기술 스택이 유효한 목록에 포함되어 있는지 확인합니다.
     */
    public static boolean isValid(String tech) {
        return OPTIONS.contains(tech);
    }

    /**
     * 모든 기술 스택 목록을 반환합니다.
     */
    public static List<String> getAllOptions() {
        return OPTIONS;
    }
}
