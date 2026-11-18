proxy_pass http://api:8080;
proxy_http_version 1.1;
proxy_set_header Host $host;
# $remote_addr (e não $proxy_add_x_forwarded_for): o cliente não consegue forjar o próprio IP (limite por IP da API)
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto https;
proxy_read_timeout 30s;
# a borda é a fonte única dos cabeçalhos de segurança (sem duplicados vindos da API)
proxy_hide_header Strict-Transport-Security;
proxy_hide_header Content-Security-Policy;
proxy_hide_header X-Content-Type-Options;
proxy_hide_header X-Frame-Options;
proxy_hide_header Referrer-Policy;
