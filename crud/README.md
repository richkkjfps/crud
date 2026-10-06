# Stockify — produtos e painéis por cliente

Sistema Java/Spring Boot com PostgreSQL e interface em português. Cada cliente tem produtos, categorias e usuários próprios.

## Acesso

Abra o projeto no IntelliJ, recarregue o Maven e execute StockifyApplication com o PostgreSQL configurado em src/main/resources/application.properties. A porta padrão é 8080. A versão conferida pelo Codex está em http://localhost:8082.

O painel original usa o código de empresa **principal**. O e-mail e a senha existentes foram preservados. O primeiro administrador foi convertido em Proprietário. Em uma instalação nova, a conta inicial é criada com a senha exibida uma única vez no console. CRUD_ADMIN_EMAIL e CRUD_ADMIN_PASSWORD permitem definir o primeiro acesso antes da criação da conta.

## Clientes e equipe

- O Proprietário abre Clientes e cadastra a empresa, um código único e o administrador inicial. Copiar link fornece a página de acesso com o código preenchido.
- Cada empresa recebe 13 categorias iniciais e pode administrar suas categorias e sua equipe.
- O login pede código da empresa, e-mail e senha. Um mesmo e-mail pode existir em empresas diferentes.
- Cadastrar na tela de acesso solicita entrada em uma empresa existente. A conta começa sem acesso; o administrador deve ativá-la em Usuários. O cadastro público não cria empresas nem concede perfil administrativo.
- Administradores gerenciam usuários e categorias de sua empresa. Usuários gerenciam produtos. Apenas o Proprietário administra os clientes.
- Desativar um cliente bloqueia suas contas e invalida sessões. Não é permitido desativar o próprio painel do Proprietário.
- Esqueci minha senha orienta a procurar o administrador; não há envio automático de e-mail.

## Produtos

Nome, preço com centavos, quantidade, categoria, marca em texto livre, código de barras, SKU, modelo e descrição. Não há painel separado de marcas nem seção de somas. O leitor de código de barras pode preencher o campo e enviar Enter para localizar ou cadastrar um produto.

Códigos de barras e SKU são únicos dentro de cada empresa; empresas diferentes podem usar os mesmos códigos. Categorias, produtos e usuários são consultados e alterados com o identificador da empresa da sessão. Uma categoria de outra empresa não pode ser vinculada ao produto, inclusive pelo banco.

## Banco e atualização

Flyway aplica V1 a V6. V4 preserva os produtos e contas existentes no painel principal e copia as marcas antigas para o texto do produto. A tabela antiga de marcas é mantida como histórico, sem gerenciamento pela interface ou API.

Senhas são armazenadas com BCrypt e não aparecem nas respostas da API. Mudanças de senha, perfil, e-mail ou acesso invalidam sessões anteriores. Alterações exigem proteção CSRF.

Ao atualizar, encerre a execução antiga no IntelliJ e inicie a versão nova: duas versões com regras diferentes não devem continuar usando o mesmo banco.

## Verificação

16 testes de produtos, contas e segurança passaram. A integração real com PostgreSQL verificou clientes separados, repetição de e-mail e códigos entre empresas, bloqueio de acesso cruzado, cadastro com aprovação e bloqueio de sessões após desativação. Os clientes de teste foram removidos; os dados originais foram preservados.

Este projeto ainda não inclui cobrança de aluguel, publicação na internet ou aplicativos Android/Windows instaláveis.

## Organização dos arquivos

HTML: src/main/resources/static/index.html e login.html. CSS: static/css/style.css. JavaScript: static/js/app.js e login.js. O Java fica em src/main/java, com controllers (rotas), model (dados), repositories (banco), dtos (campos das requisições) e config (login e segurança). As alterações do banco ficam em resources/db/migration.


## Clientes da loja, fornecedores e fiado

A aba Empresas pertence ao proprietário do Stockify e gerencia os painéis alugados. A aba Clientes pertence à loja e cadastra seus compradores, com telefone, e-mail, endereço, observações e extrato de compras e pagamentos.

Fornecedores têm cadastro próprio e o campo O que fornece para anotar produtos, serviços e condições de entrega. Cadastros de clientes e fornecedores podem ser editados.

Vendas permitem vários produtos, quantidades, pagamento à vista ou fiado. O fiado exige um cliente e pode ter entrada e vencimento. O servidor confirma os preços e baixa o estoque em uma única transação. Estoque insuficiente cancela toda a operação.

Contas a receber mostra vendas com saldo pendente, recebido, vencimento e situação. Registre pagamentos parciais ou totais no detalhe da venda; o extrato mantém o histórico e o saldo é calculado pelos recebimentos. Pagamentos acima da dívida são recusados.

V5 cria as tabelas comerciais. Os vínculos de clientes, vendas, itens e pagamentos também são protegidos por empresa no banco. Reenvios com o mesmo identificador não duplicam a venda ou o pagamento. Bloqueios de linha impedem vender estoque ou receber dívida duas vezes em requisições concorrentes. Produtos usados em vendas são preservados para manter o histórico.

A integração real verificou pagamentos parciais e quitação, rollback, isolamento de empresas, reenvios e vendas/pagamentos concorrentes. Os registros usados para a conferência foram temporários.


## Operação de vendas e estoque

O painel abre em Vendas. Primeiro pesquise o produto por nome, SKU ou código de barras e informe a quantidade. Enter na quantidade ou F6 abre a etapa de pagamento; somente nessa etapa aparece a opção de fiado. F8 confirma a venda.

Atalhos: F1 nova venda, F2 produtos, F3 clientes, F4 fornecedores, F5 pesquisar, F6 pagamento, F8 confirmar e F9 ajuda.

Proprietários da loja (ADMIN) e o administrador Stockify (OWNER) veem custo, preço de venda e lucro bruto. Funcionários (USER) não recebem custos nem lucro pela API. Empresas contratantes aparece apenas para o administrador Stockify.

V6 acrescenta custos e histórico de reposições. Repor estoque soma unidades mesmo quando o saldo está zerado e permite atualizar custo e preço. Cada venda guarda o custo daquele momento: atualizar o produto não altera o lucro de vendas anteriores. Vendas antigas sem custo registrado ficam sinalizadas e não entram no cálculo do lucro. O lucro apresentado é bruto, antes de despesas e impostos.
